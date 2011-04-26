/*******************************************************************************
 * Copyright (c) 2011 Michael Mimo Moratti.
 *  
 * Michael Mimo Moratti licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package ch.mimo.netty.handler.codec.icap;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.http.HttpMessage;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;

/**
 * This Icap chunk aggregator will receive the icap message and store the body
 * If it exists into the respective http request or response that is transported
 * with this icap message.
 * 
 * If the received icap message is a preview no action is taken!
 * QUESTION: we could allow that a preview message is collected and kept.
 * Once the 100 continue is sent the message is completed.
 * 
 * @author Michael Mimo Moratti
 *
 */

// TODO options body
public class IcapChunkAggregator extends SimpleChannelUpstreamHandler {

	private static final InternalLogger LOG = InternalLoggerFactory.getInstance(IcapChunkAggregator.class);
	
	private long maxContentLength;
	private IcapMessageWrapper message;
	
	public IcapChunkAggregator(long maxContentLength) {
		this.maxContentLength = maxContentLength;
	}
	
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    	Object msg = e.getMessage();
    	if(msg instanceof IcapMessage) {
    		LOG.debug("Aggregation of message [" + msg.getClass().getName() + "] ");
    		IcapMessage currentMessage = (IcapMessage)msg;
    		message = new IcapMessageWrapper(currentMessage);
    		if(!message.hasBody()) {
    			Channels.fireMessageReceived(ctx,message.getIcapMessage(),e.getRemoteAddress());
    			message = null;
    			return;
    		}
    	} else if(msg instanceof IcapChunkTrailer) {
    		LOG.debug("Aggregation of chunk trailer [" + msg.getClass().getName() + "] ");
    		if(message == null) {
    			ctx.sendUpstream(e);
    		} else {
    			IcapChunkTrailer trailer = (IcapChunkTrailer)msg;
    			if(trailer.getHeaderNames().size() > 0) {		
    				for(String name : trailer.getHeaderNames()) {
    					message.addHeader(name,trailer.getHeader(name));
    				}
    			}
    			Channels.fireMessageReceived(ctx,message.getIcapMessage(),e.getRemoteAddress());
    		}
    	} else if(msg instanceof IcapChunk) {
    		LOG.debug("Aggregation of chunk [" + msg.getClass().getName() + "] ");
    		IcapChunk chunk = (IcapChunk)msg;
    		if(message == null) {
    			ctx.sendUpstream(e);
    		} else if(chunk.isLast()) {
    			Channels.fireMessageReceived(ctx,message.getIcapMessage(),e.getRemoteAddress());
    			message = null;
    		} else {
	    		ChannelBuffer chunkBuffer = chunk.getContent();
	    		ChannelBuffer content = message.getContent();
    			if(content.readableBytes() > maxContentLength - chunkBuffer.readableBytes()) {
    				throw new TooLongFrameException("ICAP content length exceeded [" + maxContentLength + "] bytes");
    			} else {
    				content.writeBytes(chunkBuffer);
    			}
    		}
    	} else {
    		ctx.sendUpstream(e);
    	}
    }
    
    private final class IcapMessageWrapper {
    	
    	private IcapMessage message;
    	private HttpMessage relevantHttpMessage;
    	private boolean messageWithBody;
    	
    	public IcapMessageWrapper(IcapMessage message) {
    		this.message = message;
    		if(message.getBody() != null) {
	    		if(message.getBody().equals(IcapMessageElementEnum.REQBODY)) {
	    			relevantHttpMessage = message.getHttpRequest();
	    			messageWithBody = true;
	    		} else if(message.getBody().equals(IcapMessageElementEnum.RESBODY)) {
	    			relevantHttpMessage = message.getHttpResponse();
	    			messageWithBody = true;
	    		}
    		}
    		if(messageWithBody) {
    			if(relevantHttpMessage.getContent() == null || relevantHttpMessage.getContent().readableBytes() <= 0) {
    				relevantHttpMessage.setContent(ChannelBuffers.dynamicBuffer());
    			}
    		}
    	}
    	
    	public boolean hasBody() {
    		return messageWithBody;
    	}
    	
    	public IcapMessage getIcapMessage() {
    		return message;
    	}
    	
    	public void addHeader(String name, String value) {
    		if(messageWithBody) {
    			relevantHttpMessage.addHeader(name,value);
    		} else {
    			throw new IcapDecodingError("A message without body cannot carrie trailing headers.");
    		}
    	}
    	
    	public ChannelBuffer getContent() {
    		if(messageWithBody) {
    			return relevantHttpMessage.getContent();
    		}
    		throw new IcapDecodingError("Message stated that there is a body but nothing found in message.");
    	}
    }
}

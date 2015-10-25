package poke.client.comm;

import poke.server.ServerHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class CommInitializer extends ChannelInitializer<SocketChannel> {

		   // private final SslContext sslCtx;

		   /* public SecureChatClientInitializer(SslContext sslCtx) {
		        this.sslCtx = sslCtx;
		    }*/

		    @Override
		    public void initChannel(SocketChannel ch) throws Exception {
		    	ChannelPipeline pipeline = ch.pipeline();
				// Enable stream compression (you can remove these two if unnecessary)
				//if (compress) {
				//	pipeline.addLast("deflater", ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
				//	pipeline.addLast("inflater", ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP));
				//}

				/**
				 * length (4 bytes).
				 * 
				 * Note: max message size is 64 Mb = 67108864 bytes this defines a
				 * framer with a max of 64 Mb message, 4 bytes are the length, and strip
				 * 4 bytes
				 */
				pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(67108864, 0, 4, 0, 4));

				// pipeline.addLast("frameDecoder", new
				// DebugFrameDecoder(67108864, 0, 4, 0, 4));

				// decoder must be first
				pipeline.addLast("protobufDecoder2", new ProtobufDecoder(poke.core.Image.Request.getDefaultInstance()));
				//pipeline.addLast("protobufDecoder", new ProtobufDecoder(poke.comm.App.Request.getDefaultInstance()));
				pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
				pipeline.addLast("protobufEncoder", new ProtobufEncoder());

				// our server processor (new instance for each connection)
				pipeline.addLast("handler", new CommHandler());
		}
}

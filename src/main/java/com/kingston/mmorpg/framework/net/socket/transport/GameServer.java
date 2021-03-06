package com.kingston.mmorpg.framework.net.socket.transport;

import java.net.InetSocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kingston.mmorpg.framework.net.ServerNode;
import com.kingston.mmorpg.framework.net.socket.MessageFactory;
import com.kingston.mmorpg.framework.net.socket.codec.PacketDecoder;
import com.kingston.mmorpg.framework.net.socket.codec.PacketEncoder;
import com.kingston.mmorpg.game.ServerConfig;
import com.kingston.mmorpg.game.base.SpringContext;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateHandler;

public class GameServer implements ServerNode {

	private Logger logger = LoggerFactory.getLogger(GameServer.class);

	// 避免使用默认线程数参数
	private EventLoopGroup bossGroup = new NioEventLoopGroup(1);
	private EventLoopGroup workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors());

	private int port;

	@Override
	public void init() {
		ServerConfig serverConfig = SpringContext.getServerConfig();
		this.port = serverConfig.getServerPort();
		
		// 初始化协议表
		MessageFactory.getInstance().init();
	}

	@Override
	public void start() throws Exception {
		logger.info("socket服务已启动，正在监听用户的请求@port:" + port + "......");
		try {
			ServerBootstrap b = new ServerBootstrap();
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).option(ChannelOption.SO_BACKLOG, 1024)
					.childHandler(new ChildChannelHandler());

			b.bind(new InetSocketAddress(port)).sync();
//			f.channel().closeFuture().sync();
		} catch (Exception e) {
			logger.error("", e);

			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();

			throw e;
		}
	}

	@Override
	public void shutDown() throws Exception {
		if (bossGroup != null) {
			bossGroup.shutdownGracefully();
		}
		if (workerGroup != null) {
			workerGroup.shutdownGracefully();
		}
	}
	
	private class ChildChannelHandler extends ChannelInitializer<SocketChannel> {
		@Override
		protected void initChannel(SocketChannel arg0) throws Exception {
			ChannelPipeline pipeline = arg0.pipeline();
			pipeline.addLast(new PacketDecoder(1024 * 10, 0, 2, 0, 2));
			pipeline.addLast(new LengthFieldPrepender(2));
			pipeline.addLast(new PacketEncoder());
			// 客户端300秒没收发包，便会触发UserEventTriggered事件到IdleEventHandler
			pipeline.addLast(new IdleStateHandler(0, 0, 300));
			pipeline.addLast(new IdleEventHandler());
			pipeline.addLast(new IoEventHandler());
		}
	}

}

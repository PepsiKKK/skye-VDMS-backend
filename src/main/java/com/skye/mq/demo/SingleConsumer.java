package com.skye.mq.demo;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.nio.charset.StandardCharsets;

/**
 * 为什么消费者这边也要创建一个相同的队列：
 * 主要是为了确保该队列的存在，否则在后续的操作中可能会出现错误。
 * 主要是为了这点，即便你的队列原本并不存在，此语句也能够帮你创建一个新的队列。
 * 但是需要特别注意一点，如果你的队列已经存在，并且你想再次执行声明队列的操作，那么所有的参数必须与之前的设置完全一致。
 * 这是因为一旦一个队列已经被创建，就不能再创建一个与其参数不一致的同名队列。
 */
public class SingleConsumer {
	// 定义我们正在监听的队列名称
    private final static String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
         // 创建连接,创建连接工厂
        ConnectionFactory factory = new ConnectionFactory();
        // 设置连接工厂的主机名，这里我们连接的是本地的RabbitMQ服务器
        factory.setHost("localhost");
    	// 从工厂获取一个新的连接
        Connection connection = factory.newConnection();
        // 从连接中创建一个新的频道
        Channel channel = connection.createChannel();
    	// 创建队列,在该频道上声明我们正在监听的队列
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        // 在控制台打印等待接收消息的信息
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
    	// 定义了如何处理消息,创建一个新的DeliverCallback来处理接收到的消息
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            // 将消息体转换为字符串
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            // 在控制台打印已接收消息的信息
            System.out.println(" [x] Received '" + message + "'");
        };
        // 在频道上开始消费队列中的消息，接收到的消息会传递给deliverCallback来处理,会持续阻塞
        channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
    }
}

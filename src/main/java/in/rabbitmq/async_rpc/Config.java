package in.rabbitmq.async_rpc;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration("asyncRPCConfig")
@Profile("async_rpc")
@EnableScheduling
@EnableRabbit
@ComponentScan(basePackages = {"in.rabbitmq.async_rpc"})
public class Config {

    @Value("${queue.request}")
    private String requestQueue;
    @Value("${queue.reply}")
    private String replyQueue;
    @Value("${exchange.direct}")
    private String directExchange;
    @Value("${routingKey.request}")
    private String requestRoutingKey;
    @Value("${routingKey.reply}")
    private String replyRoutingKey;

    @Bean
    public Publisher publisher() {
        return new Publisher();
    }

    @Bean
    public Executor taskExecutor() {
        return Executors.newCachedThreadPool();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory simpleMessageListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                                      SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setTaskExecutor(taskExecutor());
        configurer.configure(factory, connectionFactory);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue replyQueueRPC() {
        return new Queue(replyQueue);
    }

    @Bean
    public Queue requestQueueRPC() {
        return new Queue(requestQueue);
    }

    @Bean
    public SimpleMessageListenerContainer rpcReplyMessageListenerContainer(ConnectionFactory connectionFactory) {
        SimpleMessageListenerContainer simpleMessageListenerContainer = new SimpleMessageListenerContainer(connectionFactory);
        simpleMessageListenerContainer.setQueues(replyQueueRPC());
        simpleMessageListenerContainer.setTaskExecutor(taskExecutor());
        return simpleMessageListenerContainer;
    }


    @Bean
    public AsyncRabbitTemplate asyncRabbitTemplate(ConnectionFactory connectionFactory) {

        AsyncRabbitTemplate asyncRabbitTemplate = new AsyncRabbitTemplate(rabbitTemplate(connectionFactory),
                        rpcReplyMessageListenerContainer(connectionFactory),
                        directExchange + "/" + replyRoutingKey);
        return asyncRabbitTemplate;
    }

    @Bean
    public DirectExchange directExchange() {
        return new DirectExchange(directExchange);
    }

    @Bean
    public List<Binding> bindings() {
        return Arrays.asList(
                        BindingBuilder.bind(requestQueueRPC()).to(directExchange()).with(requestRoutingKey),
                        BindingBuilder.bind(replyQueueRPC()).to(directExchange()).with(replyRoutingKey));
    }

    @Bean
    public Subscriber subscriber() {
        return new Subscriber();
    }

}

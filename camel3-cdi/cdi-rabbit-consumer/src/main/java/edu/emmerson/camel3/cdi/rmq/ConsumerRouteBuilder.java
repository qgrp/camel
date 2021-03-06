package edu.emmerson.camel3.cdi.rmq;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
//import org.apache.camel.component.metrics.routepolicy.MetricsRoutePolicy;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.apache.camel.http.common.HttpOperationFailedException;
//import org.apache.camel.http.base.HttpOperationFailedException;

import java.util.LinkedHashMap;

/**
 * 
 * @author emmersonmiranda
 * @link https://camel.apache.org/manual/latest/exception-clause.html
 *
 */
public class ConsumerRouteBuilder extends RouteBuilder {

    public static final String RABBITMQ_ROUTING_KEY = "rabbit.consumer";
	
    
    public static String getQueueEndpoint() {
    	StringBuilder sbConsumer = new StringBuilder();
        sbConsumer.append("rabbitmq:myexchange?")
        .append("connectionFactory=#consumerConnectionFactoryService")
        .append("&queue=myqueue")
        .append("&routingKey=main")
        .append("&durable=true")
        .append("&autoDelete=false")
        .append("&automaticRecoveryEnabled=true")
        .append("&exclusive=false")
        .append("&autoAck=false")
        .append("&concurrentConsumers=2")
        .append("&prefetchCount=2")
        .append("&prefetchEnabled=true");
        return sbConsumer.toString();
    }
    
    public static String getDLQEndpoint() {
    	StringBuilder sbConsumer = new StringBuilder();
        sbConsumer.append("rabbitmq:myexchange?")
        .append("connectionFactory=#consumerConnectionFactoryService")
        .append("&queue=myqueueDLQ")
        .append("&durable=true")
        .append("&autoDelete=false")
        .append("&automaticRecoveryEnabled=true")
        .append("&exchangePattern=InOnly")
        .append("&routingKey=dlq")
        .append("&exclusive=false")
        .append("&autoAck=false") ;
        return sbConsumer.toString();
    }
    
	@Override
    public void configure() throws Exception {
		
		boolean disableSuspension = "true".equals(System.getenv("DISABLE_SUSPENSION"));

        //
        //error handling
        //
        onException(Exception.class)
	        .maximumRedeliveries(2)
	        .handled(true)
	        .asyncDelayedRedelivery().redeliveryDelay(1000)
	        .onRedelivery(new Processor() {

				@Override
				public void process(Exchange exchange) throws Exception {
					System.out.println("redelivery");
					System.out.println("Retry counter " + exchange.getIn().getHeader(Exchange.REDELIVERY_COUNTER));
					System.out.println("Retry Max counter " + exchange.getIn().getHeader(Exchange.REDELIVERY_MAX_COUNTER));
					//System.out.println(exchange.getProperties().get("CamelExceptionCaught"));
					System.out.println(exchange.getProperties().get("CamelToEndpoint"));
					
					HttpOperationFailedException ex  = (HttpOperationFailedException) exchange.getProperties().get("CamelExceptionCaught");
					System.out.println(ex.getStatusCode());
					System.out.println(ex.getStatusText());
					System.out.println(ex.getMessage());
				}
	        	
	        }
	        )
	        .routeId("abcdef")
	        .log("\"Error reported: ${exception.message} - cannot process this message.\" - retry ${headers.rabbitmq.DELIVERY_TAG}")
	        .setHeader(RabbitMQConstants.ROUTING_KEY, constant("dlq"))
	        //sending the message to DLQ
	        .toD(getDLQEndpoint())
	        .choice()
	        	.when(constant(disableSuspension).isEqualTo(false))
	        		.log("Notifing rabbitmq consumer to stop")
			    	.process().message(m -> {
			    		int restartDelayInMilis = 30000;
			    		LinkedHashMap<String, String> body = MngtProducerRouteBuilder.buildMngtMessage(ConsumerConstants.CONSUMER_RABBITMQ_ROUTE_ID, true, RABBITMQ_ROUTING_KEY, restartDelayInMilis);
			    		m.setBody(body);
			        })
			    	.to(MngtConstants.MNGT_PRODUCER_DIRECT_ENDPOINT)
			    .otherwise()
			    	.log("Stop consumers disabled by DISABLE_SUSPENSION environment variable.")
			.end()
    	;
        
        
//        MetricsRoutePolicy mrp = MetricsFactory.createMetricsRoutePolicy(ConsumerConstants.CONSUMER_RABBITMQ_ROUTE_ID);
		
        //
        //consuming messages
        //
        from(getQueueEndpoint())
	        .routeId(ConsumerConstants.CONSUMER_RABBITMQ_ROUTE_ID)
//	        .routePolicy(mrp)
		    .to("direct:target");
	    
        //
        //processing message
        //
        from("direct:target")
	        .routeId(ConsumerConstants.CONSUMER_DIRECT_ROUTE_ID)
	        .log(">>> ...........................................................")
	        .log("Message to be sent: ${body}")
	        .log("Message to be sent: ${headers}")
	        .choice()
	        	.when(header("test-scenario").isEqualTo("ko"))
	        		.log("Simulating an exception")
		        	.process((m) -> {
		        		throw new Exception("Some error happen!");
		        	})
	        	.otherwise()
	        		.log("Congrats, message well processed!")
	        .end()
	
	        .log("<<< ...........................................................")
	        .process(new Processor() {

				@Override
				public void process(Exchange exchange) throws Exception {
					System.out.println("Just before send the request to backend.");
				}
	        	
	        })
	        .to("undertow:http://upstream:10003/microservice/myservice?httpMethodRestrict=POST&exchangePattern=InOut");
        ;
    }

}

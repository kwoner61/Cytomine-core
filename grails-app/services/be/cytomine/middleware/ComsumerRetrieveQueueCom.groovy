package be.cytomine.middleware

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.Envelope
import com.rabbitmq.client.ShutdownSignalException
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j

@Log4j
class ComsumerRetrieveQueueCom implements Consumer{

    private JsonSlurper jsonSlurper = new JsonSlurper()
    private Channel channel

    ComsumerRetrieveQueueCom(Channel chan)
    {
        channel=chan
    }

    @Override
    void handleConsumeOk(String s) {

    }

    @Override
    void handleCancelOk(String s) {

    }

    @Override
    void handleCancel(String s) throws IOException {

    }

    @Override
    void handleShutdownSignal(String s, ShutdownSignalException e) {

    }

    @Override
    void handleRecoverOk(String s) {

    }

    @Override
    void handleDelivery(String s, Envelope envelope, AMQP.BasicProperties basicProperties, byte[] bytes) throws IOException {

    }
}

package com.samagra.transformer.conversation;

import com.samagra.orchestrator.User.CampaignService;
import com.samagra.transformer.TransformerProvider;
import com.samagra.transformer.publisher.CommonProducer;
import io.fusionauth.domain.Application;
import io.fusionauth.domain.User;
import lombok.extern.slf4j.Slf4j;
import messagerosa.core.model.*;
import messagerosa.xml.XMessageParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.samagra.orchestrator.User.UserService;

@Slf4j
@Component
public class CampaignMessageMultiplexerTransformer extends TransformerProvider {

    @Autowired
    public CommonProducer kafkaProducer;

    // Not getting used in this one
    @Override
    public XMessage transform(XMessage xMessage) {
        return null;
    }

    @Override
    public List<XMessage> transformToMany(XMessage parentXMessage) {
        Application campaign;
        List<XMessage> startingMessagesForUsers = new ArrayList<>();
        try {
            campaign = CampaignService.getCampaignFromID(parentXMessage.getApp());
            List<User> users = UserService.findUsersForCampaign(parentXMessage.getApp());
            for (User user : users) {
                SenderReceiverInfo from = SenderReceiverInfo
                        .builder()
                        .userID("admin")
                        .build(); //Admin for the campaign

                SenderReceiverInfo to = SenderReceiverInfo
                        .builder()
                        .userID(user.mobilePhone)
                        .formID(parentXMessage.getTransformers().get(0).getMetaData().get("Form"))
                        .build(); //User of the campaign

                ConversationStage conversationStage = ConversationStage
                        .builder()
                        .state(ConversationStage.State.STARTING)
                        .build();

                XMessageThread thread = XMessageThread.builder()
                        .offset(0)
                        .startDate(new Date().toString())
                        .lastMessageId("0")
                        .build();

                XMessage userMessage = XMessage.builder()
                        .transformers(parentXMessage.getTransformers())
                        .from(from)
                        .to(to)
                        .messageState(XMessage.MessageState.NOT_SENT)
                        .channelURI(parentXMessage.getChannelURI())
                        .providerURI(parentXMessage.getProviderURI())
                        .timestamp(System.currentTimeMillis())
                        .app(campaign.name)
                        .thread(thread)
                        .conversationStage(conversationStage)
                        .build();

                startingMessagesForUsers.add(userMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return startingMessagesForUsers;
    }

    @KafkaListener(id = "transformer-campaign-multiplexer", topics = "CampaignMessageMultiplexer")
    public void consumeMessage(String message) throws Exception {
        log.info("CampaignMessageMultiplexer Transformer Message: " + message);
        XMessage xMessage = XMessageParser.parse(new ByteArrayInputStream(message.getBytes()));
        List<XMessage> transformedMessages = this.transformToMany(xMessage);
        for (XMessage msg : transformedMessages) {
            kafkaProducer.send(TransformerRegistry.getName(msg.getTransformers().get(0).getId()), msg.toXML());
        }
    }


}

package ca.gc.cyber.kangooroo.utils.log;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.Queue;
import java.util.stream.Collectors;

import java.util.ArrayDeque;
import java.util.List;


@Getter
public class MessageLog {

    public enum MessageType {
        INFO, DEBUG, ERROR, WARN
    }

    
    @Data
    @AllArgsConstructor
    class Message {
        

        MessageType type;
        String content;
        
        @Override
        public String toString() {
            return this.type.name() + ": " + content;
        }
        
    }

    private Queue<Message> messages;

    public MessageLog() {
        this.messages = new ArrayDeque<>();
    }

    public void addMessage(MessageType type, String content) {
        this.messages.add(new Message(type, content));
    }


    public List<String> getMessagesAsList() {
        return this.messages.stream().map(x -> x.toString()).collect(Collectors.toList());
    }


    public void warn(String content) {
        this.addMessage(MessageType.WARN, content);
    }

    public void error(String content) {
        this.addMessage(MessageType.ERROR, content);
    }

}

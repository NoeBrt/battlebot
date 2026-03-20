package algorithms.lab;

import characteristics.IRadarResult;
import robotsimulator.Brain;

import java.util.Map;

public class Broadcasting extends Brain {

    interface IMessageResult {
        String toMessageString();
        IMessageResult parseToMessageResult(String message);
    }

    record Position(Integer id, Double x, Double y) implements IMessageResult {

        @Override
        public String toMessageString() {
            return "id:" + id + ",x:" + x + ",y:" + y;
        }

        @Override
        public IMessageResult parseToMessageResult(String message) {
            String[] messageParts = message.split(",");
            Integer id = null;
            Double x = null, y = null;
            for (int i = 1; i < messageParts.length; i++) {
                String[] parts = messageParts[i].split(":");
                switch (parts[0]) {
                    case "id"   -> id = Integer.parseInt(parts[1]);
                    case "x"    -> x = Double.parseDouble(parts[1]);
                    case "y"    -> y = Double.parseDouble(parts[1]);
                }
            }
            if (id == null || x == null || y == null) {
                throw new IllegalArgumentException("Unsupported format");
            }
            return new Position(id, x, y);
        }

        @Override
        public String toString() {
            return toMessageString();
        }
    }

    static class Message {
        Map<String, IMessageResult> messages;
    }

    @Override
    public void activate() {

    }

    @Override
    public void step() {

    }
}

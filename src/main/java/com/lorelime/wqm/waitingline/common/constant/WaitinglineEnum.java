package com.lorelime.wqm.waitingline.common.constant;

public class WaitinglineEnum {

    public static enum RedisKey {
        WAITING_KEY("queue:default:waiting"),
        WAITING_TTL_KEY("queue:default:waitttl"),
        ACTIVE_KEY("queue:default:active");

        private final String value;

        RedisKey(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

}

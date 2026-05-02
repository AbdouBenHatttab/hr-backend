package tn.isetbizerte.pfe.hrbackend.modules.notification.dto;

import java.util.List;

public class NotificationBatchReadRequest {

    private List<Object> ids;

    public List<Object> getIds() {
        return ids;
    }

    public void setIds(List<Object> ids) {
        this.ids = ids;
    }
}

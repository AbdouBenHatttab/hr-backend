package tn.isetbizerte.pfe.hrbackend.modules.requests.dto;

import java.time.LocalDateTime;

public class ScheduleLoanMeetingDto {

    private LocalDateTime meetingAt;

    private String meetingNote;

    public LocalDateTime getMeetingAt() {
        return meetingAt;
    }

    public void setMeetingAt(LocalDateTime meetingAt) {
        this.meetingAt = meetingAt;
    }

    public String getMeetingNote() {
        return meetingNote;
    }

    public void setMeetingNote(String meetingNote) {
        this.meetingNote = meetingNote;
    }
}

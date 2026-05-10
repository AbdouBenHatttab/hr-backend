package tn.isetbizerte.pfe.hrbackend.modules.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashSet;
import java.util.Set;

public class UpdateMyProfileRequest {

    private String phone;
    private String address;
    private String maritalStatus;
    private Integer numberOfChildren;
    private String contactEmail;
    private String avatarPhoto;
    private String avatarColor;
    private final Set<String> presentFields = new HashSet<>();

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        presentFields.add("phone");
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        presentFields.add("address");
        this.address = address;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        presentFields.add("maritalStatus");
        this.maritalStatus = maritalStatus;
    }

    public Integer getNumberOfChildren() {
        return numberOfChildren;
    }

    public void setNumberOfChildren(Integer numberOfChildren) {
        presentFields.add("numberOfChildren");
        this.numberOfChildren = numberOfChildren;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        presentFields.add("contactEmail");
        this.contactEmail = contactEmail;
    }

    public String getAvatarPhoto() {
        return avatarPhoto;
    }

    public void setAvatarPhoto(String avatarPhoto) {
        presentFields.add("avatarPhoto");
        this.avatarPhoto = avatarPhoto;
    }

    public String getAvatarColor() {
        return avatarColor;
    }

    public void setAvatarColor(String avatarColor) {
        presentFields.add("avatarColor");
        this.avatarColor = avatarColor;
    }

    public boolean hasField(String fieldName) {
        return presentFields.contains(fieldName);
    }

    @JsonAnySetter
    public void captureUnknownField(String fieldName, Object ignored) {
        presentFields.add(fieldName);
    }
}

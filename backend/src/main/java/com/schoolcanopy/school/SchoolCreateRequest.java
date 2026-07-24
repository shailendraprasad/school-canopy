package com.schoolcanopy.school;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SchoolCreateRequest {

    @NotBlank(message = "School name is required")
    @Size(max = 100, message = "School name must not exceed 100 characters")
    private String name;

    @NotBlank(message = "School prefix is required")
    @Size(min = 2, max = 5, message = "Prefix must be 2-5 characters")
    private String prefix;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid email format")
    private String contactEmail;

    @Size(max = 200, message = "Address must not exceed 200 characters")
    private String address;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    private String initialAdminEmail;

    // Extended fields for Indian schools
    private String boardAffiliation;
    private String udiseCode;
    private String schoolType;
    private String mediumOfInstruction;
    private Integer foundedYear;
    private String city;
    private String state;
    private String pinCode;
    private String principalName;
    private String principalPhone;
    private String website;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getInitialAdminEmail() { return initialAdminEmail; }
    public void setInitialAdminEmail(String initialAdminEmail) { this.initialAdminEmail = initialAdminEmail; }
    public String getBoardAffiliation() { return boardAffiliation; }
    public void setBoardAffiliation(String v) { this.boardAffiliation = v; }
    public String getUdiseCode() { return udiseCode; }
    public void setUdiseCode(String v) { this.udiseCode = v; }
    public String getSchoolType() { return schoolType; }
    public void setSchoolType(String v) { this.schoolType = v; }
    public String getMediumOfInstruction() { return mediumOfInstruction; }
    public void setMediumOfInstruction(String v) { this.mediumOfInstruction = v; }
    public Integer getFoundedYear() { return foundedYear; }
    public void setFoundedYear(Integer v) { this.foundedYear = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getPinCode() { return pinCode; }
    public void setPinCode(String v) { this.pinCode = v; }
    public String getPrincipalName() { return principalName; }
    public void setPrincipalName(String v) { this.principalName = v; }
    public String getPrincipalPhone() { return principalPhone; }
    public void setPrincipalPhone(String v) { this.principalPhone = v; }
    public String getWebsite() { return website; }
    public void setWebsite(String v) { this.website = v; }
}

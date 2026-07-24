package com.schoolcanopy.school;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "school")
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "prefix", nullable = false, unique = true, length = 5)
    private String prefix;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "address", length = 200)
    private String address;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "brand_color", length = 7)
    private String brandColor = "#4a6b8a";

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "board_affiliation", length = 30)
    private String boardAffiliation;

    @Column(name = "udise_code", length = 15)
    private String udiseCode;

    @Column(name = "school_type", length = 20)
    private String schoolType;

    @Column(name = "medium_of_instruction", length = 30)
    private String mediumOfInstruction;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "state", length = 50)
    private String state;

    @Column(name = "pin_code", length = 6)
    private String pinCode;

    @Column(name = "principal_name", length = 100)
    private String principalName;

    @Column(name = "principal_phone", length = 20)
    private String principalPhone;

    @Column(name = "website", length = 200)
    private String website;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getBrandColor() { return brandColor; }
    public void setBrandColor(String brandColor) { this.brandColor = brandColor; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getBoardAffiliation() { return boardAffiliation; }
    public void setBoardAffiliation(String boardAffiliation) { this.boardAffiliation = boardAffiliation; }
    public String getUdiseCode() { return udiseCode; }
    public void setUdiseCode(String udiseCode) { this.udiseCode = udiseCode; }
    public String getSchoolType() { return schoolType; }
    public void setSchoolType(String schoolType) { this.schoolType = schoolType; }
    public String getMediumOfInstruction() { return mediumOfInstruction; }
    public void setMediumOfInstruction(String mediumOfInstruction) { this.mediumOfInstruction = mediumOfInstruction; }
    public Integer getFoundedYear() { return foundedYear; }
    public void setFoundedYear(Integer foundedYear) { this.foundedYear = foundedYear; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPinCode() { return pinCode; }
    public void setPinCode(String pinCode) { this.pinCode = pinCode; }
    public String getPrincipalName() { return principalName; }
    public void setPrincipalName(String principalName) { this.principalName = principalName; }
    public String getPrincipalPhone() { return principalPhone; }
    public void setPrincipalPhone(String principalPhone) { this.principalPhone = principalPhone; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
}

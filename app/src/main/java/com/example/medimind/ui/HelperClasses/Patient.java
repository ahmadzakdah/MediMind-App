package com.example.medimind.ui.HelperClasses;

public class Patient {
    private String medicalRecordNumber;
    private String name;
    private String nameLower;
    private int age;
    private String gender;
    private String recordCreationDate;
    private String currentMedications;
    private String previousSurgeries;
    private String drugAllergies;
    private String familyHistoryGeneticDiseases;
    private long createdAtMillis;

    // Required empty constructor for Firestore
    public Patient() {
    }

    public Patient(String medicalRecordNumber, String name,String nameLower, int age, String gender,
                   String recordCreationDate, String currentMedications, String previousSurgeries,
                   String drugAllergies, String familyHistoryGeneticDiseases, long createdAtMillis) {
        this.medicalRecordNumber = medicalRecordNumber;
        this.name = name;
        this.nameLower = nameLower;
        this.age = age;
        this.gender = gender;
        this.recordCreationDate = recordCreationDate;
        this.currentMedications = currentMedications;
        this.previousSurgeries = previousSurgeries;
        this.drugAllergies = drugAllergies;
        this.familyHistoryGeneticDiseases = familyHistoryGeneticDiseases;
        this.createdAtMillis = createdAtMillis;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    public String getFamilyHistoryGeneticDiseases() {
        return familyHistoryGeneticDiseases;
    }

    public void setFamilyHistoryGeneticDiseases(String familyHistoryGeneticDiseases) {
        this.familyHistoryGeneticDiseases = familyHistoryGeneticDiseases;
    }

    public String getDrugAllergies() {
        return drugAllergies;
    }

    public void setDrugAllergies(String drugAllergies) {
        this.drugAllergies = drugAllergies;
    }

    public String getPreviousSurgeries() {
        return previousSurgeries;
    }

    public void setPreviousSurgeries(String previousSurgeries) {
        this.previousSurgeries = previousSurgeries;
    }

    public String getCurrentMedications() {
        return currentMedications;
    }

    public void setCurrentMedications(String currentMedications) {
        this.currentMedications = currentMedications;
    }

    public String getRecordCreationDate() {
        return recordCreationDate;
    }

    public void setRecordCreationDate(String recordCreationDate) {
        this.recordCreationDate = recordCreationDate;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    public String getNameLower() {
        return nameLower;
    }
    public void setNameLower(String nameLower) {
        this.nameLower = nameLower;
    }

    public String getMedicalRecordNumber() {
        return medicalRecordNumber;
    }

    public void setMedicalRecordNumber(String medicalRecordNumber) {
        this.medicalRecordNumber = medicalRecordNumber;
    }
}

package edu.university.grantledger.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "department")
public class DepartmentEntity {
  @Id private UUID id;
  private String code;
  private String name;
  private boolean active;

  protected DepartmentEntity() {}

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }
}

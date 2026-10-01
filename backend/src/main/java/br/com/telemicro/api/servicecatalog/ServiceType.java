package br.com.telemicro.api.servicecatalog;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "service_types")
public class ServiceType {
    @Id private UUID id;
    @Column(nullable = false, length = 50) private String code;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false) private boolean active;
    @Column(name = "display_order", nullable = false) private short displayOrder;

    protected ServiceType() {}
    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}

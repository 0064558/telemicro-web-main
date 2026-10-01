package br.com.telemicro.api.servicecatalog;

import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {
    private final ServiceTypeRepository repository;
    public ServiceCatalogController(ServiceTypeRepository repository) { this.repository = repository; }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ServiceResponse> list() {
        return repository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(service -> new ServiceResponse(service.getCode(), service.getName())).toList();
    }

    public record ServiceResponse(String code, String name) {}
}

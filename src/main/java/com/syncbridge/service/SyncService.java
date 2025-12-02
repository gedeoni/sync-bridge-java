package com.syncbridge.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncbridge.annotation.Monitored;
import com.syncbridge.dto.SyncDtos;
import com.syncbridge.entity.Customer;
import com.syncbridge.entity.Employee;
import com.syncbridge.entity.Order;
import com.syncbridge.entity.OrderItem;
import com.syncbridge.entity.Product;
import com.syncbridge.entity.SyncHistory;
import com.syncbridge.entity.SyncStatus;
import com.syncbridge.mapper.SyncMapper;
import com.syncbridge.repository.CustomerRepository;
import com.syncbridge.repository.EmployeeRepository;
import com.syncbridge.repository.OrderRepository;
import com.syncbridge.repository.ProductRepository;
import com.syncbridge.repository.SyncHistoryRepository;

import jakarta.annotation.PostConstruct;

@Service
public class SyncService {

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private SyncHistoryRepository syncHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SyncMapper syncMapper;

    private Map<String, JpaRepository<?, ?>> repositories;
    private Map<String, Class<?>> entityClasses;
    private Map<String, Class<?>> dtoClasses;
    private Map<String, Function<Object, Object>> mappers;

    @PostConstruct
    public void init() {
        repositories = new HashMap<>();
        repositories.put("employees", employeeRepository);
        repositories.put("customers", customerRepository);
        repositories.put("products", productRepository);
        repositories.put("orders", orderRepository);

        entityClasses = new HashMap<>();
        entityClasses.put("employees", Employee.class);
        entityClasses.put("customers", Customer.class);
        entityClasses.put("products", Product.class);
        entityClasses.put("orders", Order.class);

        dtoClasses = new HashMap<>();
        dtoClasses.put("employees", SyncDtos.EmployeeDto.class);
        dtoClasses.put("customers", SyncDtos.CustomerDto.class);
        dtoClasses.put("products", SyncDtos.ProductDto.class);
        dtoClasses.put("orders", SyncDtos.OrderDto.class);

        mappers = new HashMap<>();
        mappers.put("customers", dto -> syncMapper.mapCustomer((SyncDtos.CustomerDto) dto));
        mappers.put("products", dto -> syncMapper.mapProduct((SyncDtos.ProductDto) dto));
        mappers.put("orders", dto -> syncMapper.mapOrder((SyncDtos.OrderDto) dto));
        mappers.put("employees", dto -> syncMapper.mapEmployee((SyncDtos.EmployeeDto) dto));
    }

    @Transactional
    @Monitored(name = "sync.operation", tags = {"model"})
    @SuppressWarnings("unchecked")
    public Map<String, Object> sync(String model, List<Map<String, Object>> data) {
        SyncHistory syncHistory = new SyncHistory();
        try {
            syncHistory.setPayload(objectMapper.writeValueAsString(data)); // Simplified payload storage
        } catch (Exception e) {
            syncHistory.setPayload("Error serializing payload");
        }
        syncHistory.setStatus(SyncStatus.PENDING_RETRY);
        syncHistory = syncHistoryRepository.save(syncHistory);

        JpaRepository<Object, Object> repository = (JpaRepository<Object, Object>) repositories.get(model);
        Class<?> entityClass = entityClasses.get(model);
        Class<?> dtoClass = dtoClasses.get(model);

        if (repository == null || entityClass == null || dtoClass == null) {
            syncHistory.setStatus(SyncStatus.INVALID);
            syncHistory.setFailureReason("Invalid model: " + model);
            syncHistoryRepository.save(syncHistory);
            throw new IllegalArgumentException("Invalid model: " + model);
        }

        List<Map<String, Object>> results = new ArrayList<>();

        try {
            for (Map<String, Object> itemData : data) {
                Object id = itemData.get("id");

                // First convert incoming map to the corresponding DTO, then to entity
                Object dto = objectMapper.convertValue(itemData, dtoClass);
                Function<Object, Object> mapperFn = mappers.get(model);
                Object entity;
                if (mapperFn != null) {
                    entity = mapperFn.apply(dto);
                } else {
                    entity = objectMapper.convertValue(dto, entityClass);
                }


                if (model.equals("orders") && entity instanceof Order) {
                    Order order = (Order) entity;
                    if (order.getItems() != null) {
                        for (OrderItem item : order.getItems()) {
                            item.setOrder(order);
                        }
                    }
                }

                Object savedEntity = repository.save(entity);

                // Extract ID from saved entity (assuming getId method exists or using
                // reflection/casting)
                // For simplicity, we'll use the ID from the input map if present, or try to get
                // it from the saved entity
                // Since we know the types, we can cast.
                Long savedId = null;
                switch (model) {
                    case "employees" -> savedId = ((Employee) savedEntity).getId();
                    case "customers" -> savedId = ((Customer) savedEntity).getId();
                    case "products" -> savedId = ((Product) savedEntity).getId();
                    case "orders" -> savedId = ((Order) savedEntity).getId();
                }

                Map<String, Object> result = new HashMap<>();
                result.put("id", savedId);
                result.put("status", id != null ? "updated" : "created"); // Simplified status logic
                results.add(result);
            }

            syncHistory.setStatus(SyncStatus.SUCCESSFUL);
            syncHistoryRepository.save(syncHistory);

            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            return response;

        } catch (Exception e) {
            syncHistory.setStatus(SyncStatus.FAILED);
            syncHistory.setFailureReason(e.getMessage());
            syncHistoryRepository.save(syncHistory);
            System.out.println("Exception type: " + e.getClass().getName());
            throw e;
        }
    }

    public Map<String, Object> getStats() {
        List<Object[]> statsList = syncHistoryRepository.countByStatus();
        Map<String, Object> statsSummary = new HashMap<>();

        long total = 0;
        for (Object[] stat : statsList) {
            SyncStatus status = (SyncStatus) stat[0];
            Long count = (Long) stat[1];
            statsSummary.put(status.getValue(), count);
            total += count;
        }
        statsSummary.put("total", total);

        return statsSummary;
    }
}

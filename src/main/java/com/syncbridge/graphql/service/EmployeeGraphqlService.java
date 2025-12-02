package com.syncbridge.graphql.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.syncbridge.entity.Employee;
import com.syncbridge.graphql.dto.CreateEmployeeInput;
import com.syncbridge.graphql.dto.UpdateEmployeeInput;
import com.syncbridge.repository.EmployeeRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class EmployeeGraphqlService {

    private final EmployeeRepository employeeRepository;
    private final Sinks.Many<Employee> employeeCreatedSink;

    public EmployeeGraphqlService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
        this.employeeCreatedSink = Sinks.many().multicast().directBestEffort();
    }

    public List<Employee> getEmployees(int offset, int limit) {
        Pageable pageable = toPageable(offset, limit);
        return employeeRepository.findAll(pageable).getContent();
    }

    public Employee getEmployee(Long id) {
        Optional<Employee> employee = employeeRepository.findById(id);
        return employee.orElse(null);
    }

    public List<Employee> searchEmployees(String search, int offset, int limit) {
        Pageable pageable = toPageable(offset, limit);
        return employeeRepository.searchEmployees(search, pageable).getContent();
    }

    public Employee createEmployee(CreateEmployeeInput input) {
        Employee employee = new Employee();
        employee.setId(input.getId());
        employee.setEmployeeId(input.getEmployeeId());
        employee.setFirstName(input.getFirstName());
        employee.setMiddleName(input.getMiddleName());
        employee.setLastName(input.getLastName());
        employee.setEmail(input.getEmail());
        employee.setCompany(input.getCompany());
        employee.setJobTitle(input.getJobTitle());

        Employee saved = employeeRepository.save(employee);
        employeeCreatedSink.tryEmitNext(saved);
        return saved;
    }

    public Employee updateEmployee(Long id, UpdateEmployeeInput input) {
        return employeeRepository.findById(id).map(existing -> {
            if (input.getFirstName() != null) {
                existing.setFirstName(input.getFirstName());
            }
            if (input.getLastName() != null) {
                existing.setLastName(input.getLastName());
            }
            if (input.getEmail() != null) {
                existing.setEmail(input.getEmail());
            }
            if (input.getMiddleName() != null) {
                existing.setMiddleName(input.getMiddleName());
            }
            if (input.getCompany() != null) {
                existing.setCompany(input.getCompany());
            }
            if (input.getJobTitle() != null) {
                existing.setJobTitle(input.getJobTitle());
            }
            return employeeRepository.save(existing);
        }).orElse(null);
    }

    public boolean deleteEmployee(Long id) {
        if (!employeeRepository.existsById(id)) {
            return false;
        }
        employeeRepository.deleteById(id);
        return true;
    }

    public Flux<Employee> employeeCreatedStream() {
        return employeeCreatedSink.asFlux();
    }

    private Pageable toPageable(int offset, int limit) {
        int size = limit > 0 ? limit : 10;
        int page = offset > 0 ? offset / size : 0;
        return PageRequest.of(page, size);
    }
}


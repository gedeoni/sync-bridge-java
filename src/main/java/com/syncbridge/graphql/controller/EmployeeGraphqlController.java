package com.syncbridge.graphql.controller;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import com.syncbridge.entity.Employee;
import com.syncbridge.graphql.dto.CreateEmployeeInput;
import com.syncbridge.graphql.dto.UpdateEmployeeInput;
import com.syncbridge.graphql.service.EmployeeGraphqlService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@Controller
@Validated
public class EmployeeGraphqlController {

    private final EmployeeGraphqlService employeeService;

    public EmployeeGraphqlController(EmployeeGraphqlService employeeService) {
        this.employeeService = employeeService;
    }

    @QueryMapping
    public List<Employee> employees(@Argument int offset, @Argument int limit) {
        return employeeService.getEmployees(offset, limit);
    }

    @QueryMapping
    public Employee employee(@Argument Long id) {
        return employeeService.getEmployee(id);
    }

    @QueryMapping
    public List<Employee> searchEmployees(@Argument String search, @Argument int offset, @Argument int limit) {
        return employeeService.searchEmployees(search, offset, limit);
    }

    @MutationMapping
    public Employee createEmployee(@Argument("data") @Valid CreateEmployeeInput input) {
        return employeeService.createEmployee(input);
    }

    @MutationMapping
    public Employee updateEmployee(@Argument Long id, @Argument("data") @Valid UpdateEmployeeInput input) {
        return employeeService.updateEmployee(id, input);
    }

    @MutationMapping
    public Boolean deleteEmployee(@Argument Long id) {
        return employeeService.deleteEmployee(id);
    }

    @SchemaMapping(typeName = "Employee", field = "fullName")
    public String fullName(Employee employee) {
        return Stream.of(employee.getFirstName(), employee.getMiddleName(), employee.getLastName())
                .filter(part -> part != null && !part.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    @SubscriptionMapping
    public Flux<Employee> employeeCreated() {
        return employeeService.employeeCreatedStream();
    }
}


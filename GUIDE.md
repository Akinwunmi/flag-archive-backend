# Comprehensive Guide: Maturing Your Java Spring Boot Backend

## From Proof-of-Concept to Production-Ready REST API

**Target Audience**: Experienced Frontend Developer learning Backend Development<br>
**Focus Areas**: Java, Spring Boot, SQL, Docker, and CI/CD<br>
**Project Context**: Flag Archive Backend - A Spring Boot REST API built with PostgreSQL

## Table of Contents

1. Introduction & Prerequisites
2. Phase 1: Foundation & Core Features
3. Phase 2: Quality & Reliability
4. Phase 3: Production Readiness & Security
5. Phase 4: Advanced Patterns & CI/CD
6. Learning Resources & External Documentation

## Introduction & Prerequisites

Coming from Frontend: What's Different About Backend Development?

As a frontend developer transitioning to backend development, you're familiar with:

- **Request/Response lifecycle** - You've built components that make HTTP calls
- **State management** - You understand data flow and component communication
- **UI/UX thinking** - You know how users interact with applications

In backend development, you'll focus on:

- **Data persistence** - How data is stored, retrieved, and validated
- **Business logic** - Rules that govern how data changes (the "why" behind operations)
- **Performance at scale** - Handling thousands of concurrent requests efficiently
- **System reliability** - Error handling, logging, and graceful degradation
- **Security** - Protecting sensitive data and API endpoints from unauthorized access

**The mental shift**: Frontend is about "What does the user see?", while backend is about "What can the system reliably do, and how do we prove it works?"

### Current Project State (TL;DR)

Your Flag Archive backend is a proof-of-concept (POC) with:

✅ Good: Modern Spring Boot setup, proper layered architecture, working Docker setup
❌ Missing: Complete CRUD operations, comprehensive testing, error handling, security, database schema definition

**Goal**: Transform it into a production-ready system that other developers (or a frontend team) can depend on.

## Phase 1: Foundation & Core Features

### 1.1 Understanding Your Database Schema

#### Why This Matters

Before writing code, you need a contract between your application and database. A poorly designed schema causes cascading problems: difficult queries, data inconsistencies, and performance issues.

**Frontend analogy**: A database schema is like your component props interface - if it's unclear, your entire application suffers.

#### Current Problem

Your project defines a `User` entity in Java, but **the `FlagEntity` table doesn't exist in init.sql**. The application relies on Hibernate's auto-creation (`ddl-auto: update` in `application.yaml:8`), which is risky in production.

#### Step 1: Define Your Database Schema Explicitly

**Read**: `init.sql` to understand current schema structure.

Your `users` table is created with:

```sql
CREATE TABLE users (
  id SERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  first_name VARCHAR(255),
  last_name VARCHAR(255),
  password VARCHAR(255),
  username VARCHAR(255) UNIQUE
)
```

Now, examine your `FlagEntity.java:1` model:

##### Understanding the Model:

- Fields: `id`, `name`, `type`, `uniqueId`, `altParentId`, `description`
- Annotations: `@Entity`, `@Table(name = "entities")`, `@Data` (from Lombok)

##### What you need to do:

Add the missing table definition to init.sql. Based on the entity definition:

```sql
CREATE TABLE IF NOT EXISTS entities (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(100),
  unique_id VARCHAR(255) UNIQUE,
  alt_parent_id INTEGER,
  description TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Optional: Add index for frequently queried columns
CREATE INDEX idx_entities_unique_id ON entities(unique_id);
CREATE INDEX idx_entities_type ON entities(type);
```

##### Key Concepts Explained:

| Concept     | Explanation                                         | Frontend Analogy                                      |
| ----------- | --------------------------------------------------- | ----------------------------------------------------- |
| PRIMARY KEY | Uniquely identifies each row; automatically indexed | A unique component ID in your state                   |
| NOT NULL    | Column must always have a value                     | A required prop in your component                     |
| UNIQUE      | Only one row can have this value                    | Like ensuring only one user can have a username       |
| FOREIGN KEY | Links to another table's primary key                | References between related data objects               |
| INDEX       | Speeds up queries on that column                    | Like a searchable list instead of scanning everything |
| TIMESTAMP   | Automatically records when data is created/modified | Audit trail for your changes                          |

#### Step 2: Add Audit Columns to Track Data Changes

Professional applications track when and who creates/modifies data. Add these columns:

```sql
ALTER TABLE users ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
```

Update your `User.java` model:

```java
@Data
@Entity
@Table(name = "users")
public class User {
    // ... existing fields ...

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

##### What's happening here:

- `@PrePersist` - JPA calls this method before inserting a new row
- `@PreUpdate` - JPA calls this method before updating an existing row
- `updatable = false` on `createdAt` - Prevents accidental modification of creation timestamp

#### Step 3: Understand Column Naming Conventions

Notice the mismatch: Java uses `firstName` (camelCase) but SQL uses `first_name` (snake_case).

**Why this matters**: It's a universal convention:

- **Java/OOP**: camelCase (readability for code)
- **Databases**: snake_case (readability for SQL, consistency across languages)

Always explicitly map them using `@Column`:

```java
@Column(name = "first_name")
private String firstName;
```

---

### 1.2 Implementing Complete CRUD Operations

#### What You Have vs. What You Need

**Current state** - `FlagEntityController.java`:

```
GET /api/flag-entities/{id} - Get one entity by ID
```

**Production-ready REST API** should have:

```
GET    /api/flag-entities              - List all entities (with pagination)
GET    /api/flag-entities/{id}         - Get one entity by ID
POST   /api/flag-entities              - Create a new entity
PUT    /api/flag-entities/{id}         - Update entire entity
PATCH  /api/flag-entities/{id}         - Update partial entity
DELETE /api/flag-entities/{id}         - Delete an entity
```

#### HTTP Methods: REST Conventions

| Method | Purpose                 | Safe? | Idempotent? | Use Case                     |
| ------ | ----------------------- | ----- | ----------- | ---------------------------- |
| GET    | Retrieve data           | Yes   | Yes         | Fetch without side effects   |
| POST   | Create new resource     | No    | No          | New entity, generates ID     |
| PUT    | Replace entire resource | No    | Yes         | Complete update, specific ID |
| PATCH  | Partial update          | No    | Yes         | Update specific fields       |
| DELETE | Remove resource         | No    | Yes         | Remove by ID                 |

**Idempotent = calling it 100x has same effect as calling it once**

#### Step 1: Enhance the FlagEntityRepository

Read `FlagEntityRepository.java`:

Currently:

```java
public interface FlagEntityRepository extends JpaRepository<FlagEntity, Integer> {
    FlagEntity getFlagEntityById(Integer id);
}
```

The custom `getFlagEntityById` is unnecessary - `JpaRepository` already provides `findById()`. Simplify to:

```java
public interface FlagEntityRepository extends JpaRepository<FlagEntity, Integer> {
    // JpaRepository provides:
    // findById(Integer id) - returns Optional<FlagEntity>
    // findAll() - returns List<FlagEntity>
    // save(FlagEntity) - create or update
    // delete(FlagEntity) - remove by entity
    // deleteById(Integer id) - remove by ID

    // Add custom queries for your business needs:
    List<FlagEntity> findByType(String type);
    FlagEntity findByUniqueId(String uniqueId);
}
```

##### Key Learning: JpaRepository Magic

`JpaRepository<FlagEntity, Integer>` gives you free methods:

- First generic (`FlagEntity`) = your entity type
- Second generic (`Integer`) = your primary key type

Spring Data JPA generates SQL automatically! It's like a database query builder.

#### Step 2: Build a Service Layer with Business Logic

Read `FlagEntityService.java`:

Currently, it just retrieves and converts. Add actual business logic:

```java
@Service
@RequiredArgsConstructor
public class FlagEntityService {
    private final FlagEntityRepository repository;
    private final FlagEntityMapper mapper; // You'll create this

    // CREATE
    public FlagEntityDTO create(CreateFlagEntityRequest request) {
        // Validate business rules
        if (request.getUniqueId() == null || request.getUniqueId().isBlank()) {
            throw new BadRequestException("uniqueId is required");
        }

        // Check if unique_id already exists
        if (repository.findByUniqueId(request.getUniqueId()) != null) {
            throw new DuplicateResourceException("Entity with this uniqueId already exists");
        }

        // Create and save
        FlagEntity entity = new FlagEntity();
        entity.setName(request.getName());
        entity.setType(request.getType());
        entity.setUniqueId(request.getUniqueId());
        entity.setDescription(request.getDescription());

        FlagEntity saved = repository.save(entity);
        return mapper.toDTO(saved);
    }

    // READ - one
    public FlagEntityDTO findById(Integer id) {
        FlagEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Entity not found with id: " + id));
        return mapper.toDTO(entity);
    }

    // READ - all (with pagination)
    public Page<FlagEntityDTO> findAll(Pageable pageable) {
        return repository.findAll(pageable)
            .map(mapper::toDTO);
    }

    // UPDATE
    public FlagEntityDTO update(Integer id, UpdateFlagEntityRequest request) {
        FlagEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Entity not found with id: " + id));

        if (request.getName() != null) {
            entity.setName(request.getName());
        }
        if (request.getType() != null) {
            entity.setType(request.getType());
        }
        // ... update other fields ...

        FlagEntity updated = repository.save(entity);
        return mapper.toDTO(updated);
    }

    // DELETE
    public void delete(Integer id) {
        FlagEntity entity = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Entity not found with id: " + id));
        repository.delete(entity);
    }
}
```

##### Key Concepts:

1. **Validation at Service Level** - Business rules live here, not in the controller
2. **Custom Exceptions** - Throw meaningful exceptions for specific error cases
3. **Mapping** - Convert between DTOs (API) and Entities (database)
4. **Pagination** - Handle large datasets efficiently

#### Step 3: Create Request/Response DTOs

DTOs (Data Transfer Objects) separate your `API contract` from your `database schema`. This is crucial because:

```java
// Create Request DTO - only what's needed to create
@Data
public class CreateFlagEntityRequest {
    @NotBlank(message = "name is required")
    private String name;

    private String type;

    @NotBlank(message = "uniqueId is required")
    private String uniqueId;

    private String description;
}

// Update Request DTO - all fields optional for PATCH
@Data
public class UpdateFlagEntityRequest {
    private String name;
    private String type;
    private String description;
    // Note: uniqueId is NOT updatable (it's a unique identifier)
}

// Response DTO - what we return to client
@Data
public class FlagEntityDTO {
    private Integer id;
    private String name;
    private String type;
    private String uniqueId;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

#### Step 4: Implement Global Exception Handling

Create a file `GlobalExceptionHandler.java`:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        ErrorResponse error = new ErrorResponse(
            404,
            e.getMessage(),
            System.currentTimeMillis()
        );
        return ResponseEntity.status(404).body(error);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        ErrorResponse error = new ErrorResponse(
            400,
            e.getMessage(),
            System.currentTimeMillis()
        );
        return ResponseEntity.status(400).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
            400,
            "Validation failed: " + message,
            System.currentTimeMillis()
        );
        return ResponseEntity.status(400).body(error);
    }
}

@Data
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String message;
    private long timestamp;
}
```

**Why this matters**: Instead of each endpoint handling errors differently, one central handler ensures consistent API responses.

#### Step 5: Build the Complete Controller

Update `FlagEntityController.java`:

```java
@RestController
@RequestMapping("/api/flag-entities")
@RequiredArgsConstructor
public class FlagEntityController {
    private final FlagEntityService service;

    @GetMapping
    public ResponseEntity<Page<FlagEntityDTO>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlagEntityDTO> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    public ResponseEntity<FlagEntityDTO> create(@Valid @RequestBody CreateFlagEntityRequest request) {
        FlagEntityDTO created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlagEntityDTO> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateFlagEntityRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

##### Controller Concepts:

| Annotation    | Purpose                               |
| ------------- | ------------------------------------- |
| @GetMapping   | Maps HTTP GET requests                |
| @PostMapping  | Maps HTTP POST requests               |
| @PathVariable | Extracts URL path parameter ({id})    |
| @RequestBody  | Deserializes JSON body to Java object |
| @Valid        | Triggers validation on DTO            |
| @RequestParam | Query parameters (?page=0&size=20)    |

##### Testing Your CRUD Endpoints

Use curl to test locally (after docker-compose is running):

```bash
# Create
curl -X POST http://localhost:1103/api/flag-entities \
  -H "Content-Type: application/json" \
  -d '{"name": "Japan", "type": "country", "uniqueId": "JP"}'

# Read all (with pagination)
curl http://localhost:1103/api/flag-entities?page=0&size=10

# Read one
curl http://localhost:1103/api/flag-entities/1

# Update
curl -X PUT http://localhost:1103/api/flag-entities/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Japan Updated"}'

# Delete
curl -X DELETE http://localhost:1103/api/flag-entities/1
```

---

### 1.3 Adding Input Validation

#### Why Validation Matters

Invalid data is the #1 cause of bugs. Backend validation is non-negotiable because:

- Frontend validation can be bypassed (user modifies request in DevTools)
- Database constraints fail with cryptic errors
- Business logic breaks with unexpected data

#### Spring Validation Framework

Your project already has Spring Validation. Use it:

```java
@Data
public class CreateFlagEntityRequest {
    @NotBlank(message = "name must not be blank")
    @Size(min = 1, max = 255)
    private String name;

    @NotBlank(message = "uniqueId must not be blank")
    @Size(min = 1, max = 255)
    private String uniqueId;

    @Size(max = 1000)
    private String description;

    @Pattern(regexp = "^[A-Z]{2}$", message = "type must be 2-letter country code")
    private String type;
}
```

Common validators:

- `@NotNull` - Must be non-null
- `@NotBlank` - String must not be empty/whitespace
- `@Size(min, max)` - String/collection length
- `@Email` - Valid email format
- `@Min/@Max` - Numeric ranges
- `@Pattern` - Regex matching

Trigger validation with `@Valid` on controller parameter:

```java
@PostMapping
public ResponseEntity<FlagEntityDTO> create(@Valid @RequestBody CreateFlagEntityRequest request) {
    // If validation fails, GlobalExceptionHandler catches MethodArgumentNotValidException
}
```

---

### 1.4 Understanding Dependency Injection & Spring Annotations

#### Why This Matters

Dependency Injection (DI) is how Spring manages object creation and wiring. It's similar to passing props in React - components don't create their dependencies, they receive them.

#### Key Annotations Explained

| Annotation               | Purpose                           | Pattern                                 |
| ------------------------ | --------------------------------- | --------------------------------------- |
| @SpringBootApplication   | Marks main entry point            | FlagsApplication.java                   |
| @Service                 | Marks business logic component    | Service layer classes                   |
| @Repository              | Marks data access component       | Repository interfaces (Spring Data JPA) |
| @RestController          | Marks HTTP endpoint handler       | Controller classes                      |
| @Component               | Generic Spring-managed component  | Utilities, helpers                      |
| @Autowired               | Injects dependency (old style)    | Avoid, use constructor injection        |
| @RequiredArgsConstructor | Lombok generates constructor      | Modern preferred approach               |
| @Bean                    | Manually creates Spring component | Configuration classes                   |

#### Constructor Injection (Best Practice)

```java
// ❌ Old way - Field injection
@Service
public class FlagEntityService {
    @Autowired
    private FlagEntityRepository repository;
}

// ✅ New way - Constructor injection
@Service
@RequiredArgsConstructor
public class FlagEntityService {
    private final FlagEntityRepository repository;

    // Lombok generates:
    // public FlagEntityService(FlagEntityRepository repository) {
    //     this.repository = repository;
    // }
}
```

#### Why constructor injection is better:

1. **Immutability** - `final` fields prevent accidental changes
2. **Testability** - Easy to pass mock objects in tests
3. **Clarity** - Dependencies are explicit in constructor
4. **Null safety** - Can't have null dependencies

#### Component Scanning

Spring automatically discovers and registers `@Service`, `@Repository`, `@RestController` classes. It scans the package where `@SpringBootApplication` is located and all subpackages.

Your package structure:

```
com.flagarchive.flags
├── controller
├── service
├── repository
├── model
└── dto
```

All are subpackages of `com.flagarchive.flags`, so Spring finds them automatically.

---

## Phase 2: Quality & Reliability

### 2.1 Comprehensive Testing Strategy

#### Testing Pyramid

Professional applications follow the **testing pyramid**:

```
        /\
       /  \      E2E Tests (5%)
      /----\     Integration Tests (15%)
     /      \    Unit Tests (80%)
    /--------\
```

#### Why this structure:

- **Unit tests** are fast, cheap, isolated - test one component in isolation
- **Integration tests** are slower - test components working together
- **E2E tests** are slowest, most expensive - test complete user workflows

#### Understanding Java Testing Tools

Your project has `pom.xml` with testing dependencies. Let's understand them:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

This includes:

- **JUnit 5** - Test framework and runner
- **Mockito** - Mock objects for isolation
- **AssertJ** - Fluent assertions (easier to read than assertEquals)
- **SpringTest** - Spring-aware test utilities

#### Step 1: Unit Test the Service Layer

Services contain your business logic - test them thoroughly.

Create `src/test/java/com/flagarchive/flags/service/FlagEntityServiceTest.java`:

```java
@ExtendWith(MockitoExtension.class)
class FlagEntityServiceTest {

    @Mock
    private FlagEntityRepository repository;

    @InjectMocks
    private FlagEntityService service;

    private FlagEntity testEntity;
    private FlagEntityDTO testDTO;

    @BeforeEach
    void setUp() {
        testEntity = new FlagEntity();
        testEntity.setId(1);
        testEntity.setName("Japan");
        testEntity.setUniqueId("JP");

        testDTO = new FlagEntityDTO();
        testDTO.setId(1);
        testDTO.setName("Japan");
        testDTO.setUniqueId("JP");
    }

    @Test
    void testFindByIdSuccess() {
        // Arrange - set up test data
        when(repository.findById(1)).thenReturn(Optional.of(testEntity));

        // Act - call the method
        FlagEntityDTO result = service.findById(1);

        // Assert - verify result
        assertThat(result)
            .isNotNull()
            .extracting("id", "name", "uniqueId")
            .containsExactly(1, "Japan", "JP");

        verify(repository).findById(1);
    }

    @Test
    void testFindByIdNotFound() {
        // Arrange
        when(repository.findById(999)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> service.findById(999))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void testCreateSuccess() {
        // Arrange
        CreateFlagEntityRequest request = new CreateFlagEntityRequest();
        request.setName("Japan");
        request.setUniqueId("JP");

        when(repository.findByUniqueId("JP")).thenReturn(null);
        when(repository.save(any(FlagEntity.class))).thenReturn(testEntity);

        // Act
        FlagEntityDTO result = service.create(request);

        // Assert
        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getName()).isEqualTo("Japan");

        verify(repository).save(any(FlagEntity.class));
    }

    @Test
    void testCreateDuplicateUniqueId() {
        // Arrange
        CreateFlagEntityRequest request = new CreateFlagEntityRequest();
        request.setUniqueId("JP");

        when(repository.findByUniqueId("JP")).thenReturn(testEntity);

        // Act & Assert
        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(DuplicateResourceException.class);

        verify(repository, never()).save(any());
    }
}
```

##### Test Concepts:

| Concept                     | Meaning                                              |
| --------------------------- | ---------------------------------------------------- |
| `@Mock`                     | Creates a fake object that returns predefined values |
| `@InjectMocks`              | Injects mocks into the class being tested            |
| `when(...).thenReturn(...)` | Set up mock behavior                                 |
| `verify()`                  | Assert that a method was called                      |
| `@BeforeEach`               | Runs before each test (setup)                        |
| `assertThat()`              | Fluent assertions (AssertJ)                          |

#### Step 2: Integration Test the Controller Layer

Integration tests use real Spring context and test endpoint behavior.

Create `src/test/java/com/flagarchive/flags/controller/FlagEntityControllerTest.java`:

```java
@WebMvcTest(FlagEntityController.class)
@MockBean(FlagEntityService.class)
class FlagEntityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlagEntityService service;

    @Test
    void testGetById_ReturnsEntity() throws Exception {
        // Arrange
        FlagEntityDTO dto = new FlagEntityDTO();
        dto.setId(1);
        dto.setName("Japan");

        when(service.findById(1)).thenReturn(dto);

        // Act & Assert
        mockMvc.perform(get("/api/flag-entities/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Japan"));
    }

    @Test
    void testGetById_NotFound() throws Exception {
        when(service.findById(999))
            .thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/api/flag-entities/999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void testCreate_ReturnsCreated() throws Exception {
        FlagEntityDTO dto = new FlagEntityDTO();
        dto.setId(1);

        when(service.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/flag-entities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Japan",
                        "uniqueId": "JP"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1));
    }
}
```

##### What's happening:

- `@WebMvcTest` - Loads only the controller, not the whole Spring context (faster)
- `MockMvc` - Simulates HTTP requests without starting a server
- `jsonPath()` - Navigate JSON response structure (like CSS selectors for APIs)

#### Step 3: Use TestContainers for True Integration Tests

TestContainers spins up real PostgreSQL in Docker for integration tests:

Add to pom.xml:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
```

Create `src/test/java/com/flagarchive/flags/integration/FlagEntityIntegrationTest.java`:

```java
@SpringBootTest
@Testcontainers
class FlagEntityIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("flags_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FlagEntityRepository repository;

    @Test
    void testCreateAndRetrieveFlagEntity() {
        // Act & Assert - test against real database
        FlagEntity entity = new FlagEntity();
        entity.setName("Japan");
        entity.setUniqueId("JP");

        FlagEntity saved = repository.save(entity);
        assertThat(saved.getId()).isNotNull();

        FlagEntity retrieved = repository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getName()).isEqualTo("Japan");
    }
}
```

##### TestContainers Benefits:

- Real database for testing (not mocked)
- Tests actual SQL behavior
- Isolated environment - each test gets fresh database
- Automatic cleanup

#### Step 4: Run Tests and Generate Coverage Report

Add to pom.xml:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.11</version>
</plugin>
```

Run tests:

```bash
./mvnw test                    # Run all tests
./mvnw test -Dtest=FlagEntity*  # Run specific tests
./mvnw jacoco:report           # Generate coverage report
```

Report available at: `target/site/jacoco/index.html`

##### Coverage Goals:

- Service layer: 80%+ coverage
- Controller layer: 70%+ coverage
- Models: Don't test auto-generated Lombok code

### 2.2 API Documentation with OpenAPI/Swagger

#### Why Documentation Matters

Your `FlagEntityController.java` has SpringDoc OpenAPI integrated, but endpoints lack documentation. Without it, frontend developers must:

- Read source code (inefficient)
- Guess required fields
- Guess error responses

**Solution: OpenAPI annotations** - Self-documenting code.

#### Adding OpenAPI Annotations

Install SpringDoc OpenAPI (already in pom.xml):

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
</dependency>
```

Annotate your controller:

```java
@RestController
@RequestMapping("/api/flag-entities")
@RequiredArgsConstructor
@Tag(name = "Flag Entities", description = "Manage flag entities")
public class FlagEntityController {

    @GetMapping
    @Operation(summary = "List all flag entities", description = "Returns paginated list of flag entities")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Success"),
        @ApiResponse(responseCode = "400", description = "Invalid pagination parameters")
    })
    public ResponseEntity<Page<FlagEntityDTO>> getAll(
            @ParameterObject
            @PageableDefault(size = 20, page = 0)
            Pageable pageable) {
        return ResponseEntity.ok(service.findAll(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get entity by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Entity found"),
        @ApiResponse(responseCode = "404", description = "Entity not found")
    })
    public ResponseEntity<FlagEntityDTO> getById(
            @PathVariable
            @Parameter(description = "Entity ID")
            Integer id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create new flag entity")
    @ApiResponse(responseCode = "201", description = "Entity created successfully")
    public ResponseEntity<FlagEntityDTO> create(
            @Valid @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Entity data",
                required = true
            )
            CreateFlagEntityRequest request) {
        FlagEntityDTO created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

Also annotate your DTOs:

```java
@Data
@Schema(description = "DTO for creating a flag entity")
public class CreateFlagEntityRequest {
    @Schema(description = "Name of the entity", example = "Japan")
    @NotBlank(message = "name is required")
    private String name;

    @Schema(description = "Unique identifier", example = "JP")
    @NotBlank(message = "uniqueId is required")
    private String uniqueId;

    @Schema(description = "Entity type", example = "country")
    private String type;

    @Schema(description = "Detailed description")
    private String description;
}
```

#### Accessing Documentation

After annotating, visit:

- **Swagger UI**: `http://localhost:1103/api/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:1103/api/v3/api-docs`

Frontend developers can:

- See all endpoints
- Try endpoints directly in Swagger UI
- Download OpenAPI spec for code generation

---

## Phase 3: Production Readiness & Security

### 3.1 Authentication & Authorization

#### Why This Matters

Currently, anyone with your API URL can create, read, update, delete data. Production systems need:

- **Authentication** - Who are you? (login with credentials)
- **Authorization** - What can you do? (permissions)

#### Step 1: Add Spring Security

Add to pom.xml:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

#### Step 2: Password Hashing

Never store plain passwords. Use BCrypt:

```java
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserDTO createUser(CreateUserRequest request) {
        // Hash password before saving
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(hashedPassword);
        user.setUsername(request.getUsername());

        User saved = repository.save(user);
        return toDTO(saved);
    }
}
```

Create `SecurityConfig.java`:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable() // Disable for API (stateless)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/swagger-ui/**", "/api/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(withDefaults()); // Enable basic auth for now

        return http.build();
    }
}
```

#### Step 3: JWT Authentication (Token-based)

JWT (JSON Web Tokens) allow stateless authentication - frontend sends token, no server session needed.

Create `JwtTokenProvider.java`:

```java
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret:your-secret-key-change-in-production}")
    private String secretKey;

    @Value("${app.jwt.expiration:86400000}")
    private long tokenExpiration;

    public String generateToken(String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + tokenExpiration);

        return Jwts.builder()
            .subject(email)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()), SignatureAlgorithm.HS512)
            .compact();
    }

    public String getEmailFromToken(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

Create an authentication controller:

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new BadRequestException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Invalid credentials");
        }

        String token = tokenProvider.generateToken(user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token));
    }
}

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
}
```

Add to `application.yaml`:

```yml
app:
  jwt:
    secret: ${JWT_SECRET:change-this-in-production}
    expiration: 86400000 # 24 hours
```

##### Frontend Usage

```js
// Login to get token
const response = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'user@example.com', password: 'password' }),
});

const { token } = await response.json();

// Use token in subsequent requests
fetch('/api/flag-entities', {
  headers: { Authorization: `Bearer ${token}` },
});
```

### 3.2 Error Handling & Logging

#### Structured Logging

Replace console.log-style logging with structured logs for production debugging.

Add to pom.xml:

```xml
<dependency>
    <groupId>io.github.microutils</groupId>
    <artifactId>kotlin-logging-jvm</artifactId>
    <version>3.0.5</version>
</dependency>
```

Create a logging utility:

```java
@Service
@RequiredArgsConstructor
public class FlagEntityService {
    private static final Logger logger = LoggerFactory.getLogger(FlagEntityService.class);

    public FlagEntityDTO create(CreateFlagEntityRequest request) {
        logger.info("Creating flag entity with uniqueId: {}", request.getUniqueId());

        try {
            // ... business logic ...
            logger.info("Flag entity created successfully with id: {}", saved.getId());
            return mapper.toDTO(saved);
        } catch (Exception e) {
            logger.error("Error creating flag entity: {}", e.getMessage(), e);
            throw e;
        }
    }
}
```

Update `application.yaml`:

```yml
logging:
  level:
    root: INFO
    com.flagarchive.flags: DEBUG
  pattern:
    console: '%d{yyyy-MM-dd HH:mm:ss} - %msg%n'
```

#### Comprehensive Exception Handling

Expand `GlobalExceptionHandler.java`:

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity.status(404).body(
            new ErrorResponse(404, e.getMessage(), System.currentTimeMillis())
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException e) {
        log.warn("Bad request: {}", e.getMessage());
        return ResponseEntity.status(400).body(
            new ErrorResponse(400, e.getMessage(), System.currentTimeMillis())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", message);
        return ResponseEntity.status(400).body(
            new ErrorResponse(400, "Validation failed: " + message, System.currentTimeMillis())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(500).body(
            new ErrorResponse(500, "Internal server error", System.currentTimeMillis())
        );
    }
}
```

### 3.3 Database Migrations with Flyway

#### Why Migrations Matter

Current setup uses `ddl-auto: update`, which:

- ❌ Doesn't track schema changes
- ❌ Can't rollback
- ❌ Not safe in production

**Solution: Flyway** - Version control for databases.

#### Setup Flyway

Add to pom.xml:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>9.22.3</version>
</dependency>
```

Update `application.yaml`:

```yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate # Don't auto-create, use migrations
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Create migration files in `src/main/resources/db/migration/`:

`V1\_\_Initial_Schema.sql`:

```sql
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    password VARCHAR(255),
    username VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE entities (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(100),
    unique_id VARCHAR(255) UNIQUE,
    alt_parent_id INTEGER,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_entities_unique_id ON entities(unique_id);
CREATE INDEX idx_entities_type ON entities(type);
```

`V2__Add_User_Roles.sql`:

```sql
CREATE TABLE roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    user_id INTEGER NOT NULL,
    role_id INTEGER NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
);

INSERT INTO roles(name) VALUES ('ADMIN'), ('USER');
```

**Naming Convention**: `V{version}__{description}.sql`

- Version must be sequential
- Two underscores separate version from description
- Flyway tracks executed migrations in `flyway_schema_history` table

### 3.4 Environment Configuration Management

#### The Problem

Hard-coded values in source code are dangerous:

```yml
# ❌ Bad - secrets in git
spring:
  datasource:
    password: mySecretPassword
```

#### Solution: Environment Variables

Update `application.yaml`:

```yml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/flags_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver

server:
  port: ${SERVER_PORT:1103}

app:
  jwt:
    secret: ${JWT_SECRET:dev-secret-change-in-production}
    expiration: ${JWT_EXPIRATION:86400000}
```

Usage:

```bash
# Development
export DB_URL=jdbc:postgresql://localhost:5432/flags_db
export DB_USER=postgres
export DB_PASSWORD=postgres
./mvnw spring-boot:run

# Production (in CI/CD or deployment script)
export DB_URL=jdbc:postgresql://prod-db.example.com/flags_db
export DB_USER=prod_user
export DB_PASSWORD=... # From secrets manager
java -jar flags-0.0.1-SNAPSHOT.jar
```

#### Using .env Files Locally

Create `.env` file (don't commit to git):

```
DB_URL=jdbc:postgresql://postgres:5432/flags_db
DB_USER=flagsuser
DB_PASSWORD=flagspassword
JWT_SECRET=local-dev-secret-12345
```

Update `docker-compose.yml`:

```yml
services:
  backend:
    environment:
      DB_URL: jdbc:postgresql://postgres:5432/flags_db
      DB_USER: ${DB_USER}
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
```

Run:

```bash
docker-compose up  # Uses .env automatically
```

Add to .gitignore:

```
.env
.env.local
*.env
```

---

## Phase 4: Advanced Patterns & CI/CD

### 4.1 Building for Production

#### Understanding Maven Build Process

Your `pom.xml` defines how Maven builds your project:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
            <version>4.0.1</version>
        </plugin>
    </plugins>
</build>
```

Build process:

```
mvn clean package
├── clean         - Delete target/ directory
├── compile       - Compile Java to bytecode
├── test          - Run unit tests
└── package       - Create JAR (executable)
```

Result: `flags-0.0.1-SNAPSHOT.jar` - Ready to deploy!

#### Building a Production JAR

```bash
./mvnw clean package -DskipTests  # Build without tests (faster)
java -jar target/flags-0.0.1-SNAPSHOT.jar  # Run the JAR
```

The JAR is **self-contained** - includes:

- Spring Boot runtime
- All dependencies
- Application code
- Resources (YAML, SQL migrations, templates)

You can deploy just this one file anywhere Java is installed.

#### Optimizing Jar Size

Your current build includes unnecessary files. Reduce JAR size:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-tomcat</artifactId>
            </exclude>
        </excludes>
    </configuration>
</plugin>
```

### 4.2 Docker Best Practices

#### Understanding Your Current Dockerfile

Read `Dockerfile`:

```Dockerfile
FROM eclipse-temurin:25-jdk-alpine as build
WORKDIR /app
COPY . .
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:25-jre-alpine
COPY --from=build /app/target/flags-*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

#### Multi-stage build concept:

```
Stage 1 (build):   Full JDK needed to compile
                   ↓
                   Creates JAR (large)
                   ↓
Stage 2 (runtime): Only JRE needed to run
                   ↓ Copy JAR from stage 1
                   Final image (smaller)
```

#### Benefits:

- Final image only contains JRE (not JDK)
- Smallest image = faster deployment
- Smaller attack surface

#### Optimizing the Dockerfile

Add a `.dockerignore` to skip unnecessary files:

```
.git
.github
target/
*.log
.env
.env.local
.vscode
.idea
node_modules
*.md
```

Update Dockerfile for caching efficiency:

```Dockerfile
FROM eclipse-temurin:25-jdk-alpine as build

WORKDIR /app

# Copy POM first (cached separately from source)
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Download dependencies (cached layer)
RUN ./mvnw dependency:go-offline

# Copy source code
COPY src src

# Build
RUN ./mvnw package -DskipTests

# Runtime stage
FROM eclipse-temurin:25-jre-alpine

# Add non-root user for security
RUN addgroup -g 1000 spring && adduser -D -u 1000 -G spring spring

WORKDIR /app

# Copy JAR from build stage
COPY --from=build --chown=spring:spring /app/target/flags-*.jar app.jar

# Switch to non-root user
USER spring

EXPOSE 1103

ENTRYPOINT ["java", "-jar", "app.jar"]
```

Key improvements:

- POM copied separately → cached dependency downloads
- Non-root user → security improvement
- Explicit user ownership → proper file permissions

#### Building and Running Locally

```bash
# Build Docker image
docker build -t flag-archive-backend:latest .

# Run container
docker run -p 1103:1103 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/flags_db \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  flag-archive-backend:latest

# Or use docker-compose
docker-compose up --build
```

### 4.3 Continuous Integration/Continuous Deployment (CI/CD)

#### What is CI/CD?

- **CI (Continuous Integration)**: Automatically test code when pushed
- **CD (Continuous Deployment)**: Automatically deploy tested code to production

#### Benefits:

- Catch bugs early (before production)
- Reduce manual testing
- Deploy faster and more reliably
- Catch security issues automatically

#### Setting Up GitHub Actions

Create `.github/workflows/build.yml`:

```yml
name: Build and Test

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_DB: flags_test
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 25
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Build and test
        env:
          DB_URL: jdbc:postgresql://localhost:5432/flags_test
          DB_USER: test
          DB_PASSWORD: test
        run: ./mvnw clean verify

      - name: Generate coverage report
        run: ./mvnw jacoco:report

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
```

#### What this does:

1. Triggers on push/PR to main/develop branches
2. Spins up PostgreSQL test database
3. Runs ./mvnw clean verify (compile + test + build)
4. Generates test coverage report
5. Uploads to Codecov (tracks coverage over time)

#### Multi-Environment Deployment

Create `.github/workflows/deploy.yml`:

```yml
name: Deploy to Production

on:
  push:
    branches: [main]
    tags: ['v*']

jobs:
  deploy:
    runs-on: ubuntu-latest
    if: startsWith(github.ref, 'refs/tags/v')

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: maven

      - name: Build production JAR
        run: ./mvnw clean package -DskipTests

      - name: Build and push Docker image
        env:
          REGISTRY: ghcr.io
          IMAGE_NAME: ${{ github.repository }}
        run: |
          docker build -t $REGISTRY/$IMAGE_NAME:latest .
          docker tag $REGISTRY/$IMAGE_NAME:latest $REGISTRY/$IMAGE_NAME:${{ github.ref_name }}
          # Push to container registry
          # (requires authentication setup)

      - name: Deploy to production
        env:
          DEPLOY_KEY: ${{ secrets.DEPLOY_KEY }}
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
        run: |
          # SSH to production server and pull new image
          # Deploy using your infrastructure (AWS, Azure, DigitalOcean, etc.)
```

#### Secrets setup:

1. Go to GitHub repo → Settings → Secrets and variables → Actions
2. Add:
   - `DEPLOY_KEY` - Private SSH key for deployment server
   - `DEPLOY_HOST` - Production server address

### Local Testing CI/CD Workflows

Use `act` to test workflows locally:

```bash
brew install act  # macOS

# Run workflow locally
act push -j build
```

### 4.4 Monitoring & Observability in Production

#### Adding Spring Boot Actuator

Actuator exposes application metrics and health checks:

Add to pom.xml:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Update `application.yaml`:

```yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
```

Access metrics:

- **Health**: `http://localhost:1103/api/actuator/health`
- **Metrics**: `http://localhost:1103/api/actuator/metrics`
- **Prometheus format**: `http://localhost:1103/api/actuator/prometheus`

#### Understanding Key Metrics

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    }
  }
}
```

#### What matters:

- `status: UP` - Application running
- Database connectivity - Can reach PostgreSQL
- JVM memory - Not running out of memory
- Request latency - Performance is acceptable

#### Setting Up Prometheus & Grafana

Create `docker-compose.prod.yml` for production monitoring:

```yml
version: '3.8'

services:
  backend:
    # ... your backend service ...
    expose:
      - '1103'

  postgres:
    # ... your postgres service ...

  prometheus:
    image: prom/prometheus:latest
    ports:
      - '9090:9090'
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'

  grafana:
    image: grafana/grafana:latest
    ports:
      - '3000:3000'
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana-storage:/var/lib/grafana

volumes:
  grafana-storage:
```

Create `prometheus.yml`:

```yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'flag-archive'
    metrics_path: '/api/actuator/prometheus'
    static_configs:
      - targets: ['localhost:1103']
```

Access:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

### 4.5 Scalability Patterns

#### Pagination for Large Datasets

Instead of returning all entities, paginate:

Your controller already supports pagination:

```java
@GetMapping
public ResponseEntity<Page<FlagEntityDTO>> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size);
    return ResponseEntity.ok(service.findAll(pageable));
}
```

Usage:

```bash
GET /api/flag-entities?page=0&size=50  # First 50 entities
GET /api/flag-entities?page=1&size=50  # Next 50 entities
```

Response includes:

```json
{
  "content": [
    /* array of entities */
  ],
  "totalElements": 1000,
  "totalPages": 20,
  "currentPage": 0,
  "pageSize": 50
}
```

#### Caching for Performance

Add caching to expensive queries:

Add to pom.xml:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>io.github.benas</groupId>
    <artifactId>easy-random-core</artifactId>
    <version>5.0.0</version>
</dependency>
```

Enable caching in main application:

```java
@SpringBootApplication
@EnableCaching
public class FlagsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlagsApplication.class, args);
    }
}
```

Cache frequently accessed data:

```java
@Service
@RequiredArgsConstructor
public class FlagEntityService {

    @Cacheable(value = "flagEntities", key = "#id")
    public FlagEntityDTO findById(Integer id) {
        // Only called if not in cache
        return toDTO(repository.findById(id).orElseThrow());
    }

    @CacheEvict(value = "flagEntities", key = "#id")
    public void delete(Integer id) {
        repository.deleteById(id);
    }
}
```

Production setup with Redis:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

`application.yaml`:

```yml
spring:
  cache:
    type: redis
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}
```

#### Database Query Optimization

Add `@EntityGraph` for efficient queries:

```java
public interface FlagEntityRepository extends JpaRepository<FlagEntity, Integer> {
    @EntityGraph(attributePaths = "users")  // Load related data in one query
    List<FlagEntity> findAll();
}
```

Prevents N+1 query problem:

- ❌ Bad: 1 query for entities + 1 query per entity = N+1 queries
- ✅ Good: 1 query with joins = single query

---

## Learning Resources & External Documentation

### Spring Boot & Spring Framework

- Official Spring Boot Docs: https://spring.io/projects/spring-boot
- Spring Data JPA Guide: https://spring.io/projects/spring-data-jpa
- Spring Security Docs: https://spring.io/projects/spring-security
- Spring Boot Testing: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing

### Java Fundamentals

- Java Official Tutorials: https://docs.oracle.com/javase/tutorial/
- Baeldung (Excellent guides): https://www.baeldung.com/java-tutorial
- Modern Java Features: https://docs.oracle.com/en/java/javase/25/docs/api/

### SQL & Database Design

- PostgreSQL Official Docs: https://www.postgresql.org/docs/
- SQL Best Practices: https://use-the-index-luke.com/
- Database Normalization: https://en.wikipedia.org/wiki/Database_normalization

### Docker & Containerization

- Docker Official Docs: https://docs.docker.com/
- Docker Compose Reference: https://docs.docker.com/compose/compose-file/
- Best Practices: https://docs.docker.com/develop/dev-best-practices/

### Testing

- JUnit 5 Documentation: https://junit.org/junit5/docs/current/user-guide/
- Mockito Documentation: https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
- TestContainers: https://www.testcontainers.org/
- AssertJ: https://assertj.github.io/assertj-core-features-highlight.html

### API Documentation

- OpenAPI/Swagger Specification: https://spec.openapis.org/
- SpringDoc OpenAPI: https://springdoc.org/
- REST API Best Practices: https://restfulapi.net/

### DevOps & CI/CD

- GitHub Actions Docs: https://docs.github.com/en/actions
- Flyway Database Migrations: https://flywaydb.org/documentation/
- Prometheus Monitoring: https://prometheus.io/docs/
- Grafana Dashboards: https://grafana.com/docs/

### Additional Learning

- Lombok Annotations: https://projectlombok.org/features/all
- JWT (JSON Web Tokens): https://jwt.io/
- 12 Factor App: https://12factor.net/ (application design best practices)
- Design Patterns: https://refactoring.guru/design-patterns/java

---

## Summary & Next Steps

### What You've Learned

This guide covers the transformation of a POC into production-ready backend:

| Phase | Focus                   | Key Technologies                      |
| ----- | ----------------------- | ------------------------------------- |
| 1     | CRUD, REST, Data Models | Spring Data, JPA, Repositories        |
| 2     | Quality & Testing       | JUnit 5, Mockito, TestContainers      |
| 3     | Security & Production   | Spring Security, JWT, Flyway, Logging |
| 4     | Deployment & Scale      | Docker, CI/CD, Monitoring, Caching    |

### Recommended Implementation Order

1. Week 1-2: Phase 1 (Schema, CRUD, Validation)
2. Week 3: Phase 2 (Testing - focus on Service & Controller tests)
3. Week 4-5: Phase 3 (Security, Error Handling, Migrations)
4. Week 6-8: Phase 4 (CI/CD, Docker optimization, Monitoring)

### Building Confidence

- **After Phase 1:** You can handle API requests and persist data
- **After Phase 2:** You can prove your code works with tests
- **After Phase 3:** You can securely deploy to production
- **After Phase 4:** You can monitor and scale in production

### Common Pitfalls to Avoid

1. ❌ Skipping tests - "I'll test manually" (doesn't scale)
2. ❌ Hard-coded secrets - "It's just development" (leaks in git)
3. ❌ No error handling - "Exceptions shouldn't happen" (they do)
4. ❌ Ignoring database schema - "Hibernate auto-creates" (unmaintainable)
5. ❌ No logging - "Errors will be obvious" (they won't in production)

### You're Ready When

- ✅ All CRUD endpoints have tests
- ✅ Sensitive data is never hard-coded
- ✅ API documentation is complete
- ✅ Error responses are consistent
- ✅ Database migrations are version-controlled
- ✅ CI/CD pipeline automatically tests pull requests
- ✅ Application metrics are visible in production

---

## End of Guide

Good luck with your backend learning journey! The path from frontend to full-stack developer is challenging but rewarding. Focus on **understanding the why** behind each pattern, not just memorizing syntax.

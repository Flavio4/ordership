package com.rtz.ordership;

import com.jayway.jsonpath.JsonPath;
import com.rtz.ordership.repository.CustomerAddressRepository;
import com.rtz.ordership.repository.OrderItemRepository;
import com.rtz.ordership.repository.ProductRepository;
import com.rtz.ordership.tenant.StoreContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con dos tiendas en la base, cada una ve y modifica solo lo suyo. Contra Postgres real (el filtro es de Hibernate). */
@SpringBootTest(properties = {"app.shopify.webhook-secret=", "app.shopify.allow-unsigned-webhooks=true"})
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoreIsolationTest {

    private static final String PASSWORD = "clave123";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Asuncion"));

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerAddressRepository customerAddressRepository;

    private UUID storeA;
    private UUID storeB;
    private UUID multiUser;
    private UUID userB;
    private String tokenA;
    private String tokenB;
    private String tokenMulti;
    private String tokenWithoutStores;

    private String zoneA;
    private String customerA;
    private String addressA;
    private String productA;
    private String carrierA;
    private String orderA;

    @BeforeAll
    void setUp() throws Exception {
        storeA = createStore("Tienda A", "tienda-a.myshopify.com");
        storeB = createStore("Tienda B", "tienda-b.myshopify.com");

        UUID userA = createUser("ana@tienda-a.com");
        addMember(storeA, userA, "ADMIN");
        userB = createUser("beto@tienda-b.com");
        addMember(storeB, userB, "ADMIN");
        multiUser = createUser("multi@tiendas.com");
        addMember(storeA, multiUser, "ADMIN");
        addMember(storeB, multiUser, "DELIVERY");
        createUser("nadie@tiendas.com");

        tokenA = login("ana@tienda-a.com");
        tokenB = login("beto@tienda-b.com");
        tokenMulti = login("multi@tiendas.com");
        tokenWithoutStores = login("nadie@tiendas.com");

        zoneA = create(tokenA, "/api/zones", """
                {"name": "Centro"}""");
        customerA = create(tokenA, "/api/customers", """
                {"fullName": "Cliente A", "phone": "0981111111"}""");
        addressA = create(tokenA, "/api/customers/" + customerA + "/addresses", """
                {"zoneId": "%s", "label": "Casa"}""".formatted(zoneA));
        productA = create(tokenA, "/api/products", productJson("SKU-1"));
        carrierA = create(tokenA, "/api/carriers", """
                {"name": "Repartidor A", "type": "OWN"}""");
        orderA = create(tokenA, "/api/orders", """
                {"customerId": "%s", "customerAddressId": "%s", "deliveryDate": "%s",
                 "items": [{"productId": "%s", "quantity": 2}]}""".formatted(customerA, addressA, TODAY, productA));
    }

    @Test
    void ordersOfAnotherStoreAreInvisible() throws Exception {
        perform(get("/api/orders"), tokenA).andExpect(status().isOk()).andExpect(jsonPath("$..id", hasItem(orderA)));

        perform(get("/api/orders"), tokenB).andExpect(status().isOk()).andExpect(jsonPath("$..id", not(hasItem(orderA))));
        perform(get("/api/orders/" + orderA), tokenB).andExpect(status().isNotFound());
        perform(patch("/api/orders/" + orderA + "/notes").content("""
                {"notes": "de otra tienda"}"""), tokenB).andExpect(status().isNotFound());
        perform(patch("/api/orders/" + orderA + "/status").content("""
                {"status": "CANCELLED"}"""), tokenB).andExpect(status().isNotFound());
        perform(get("/api/customers/" + customerA + "/orders"), tokenB).andExpect(status().isNotFound());
    }

    @Test
    void customersAndAddressesOfAnotherStoreAreInvisible() throws Exception {
        perform(get("/api/customers"), tokenB).andExpect(status().isOk())
                .andExpect(jsonPath("$..id", not(hasItem(customerA))));
        perform(get("/api/customers/" + customerA), tokenB).andExpect(status().isNotFound());
        perform(put("/api/customers/" + customerA).content("""
                {"fullName": "Cambiado"}"""), tokenB).andExpect(status().isNotFound());
        perform(get("/api/customers/" + customerA + "/addresses"), tokenB).andExpect(status().isNotFound());
        perform(put("/api/customers/" + customerA + "/addresses/" + addressA).content("""
                {"label": "Cambiada"}"""), tokenB).andExpect(status().isNotFound());
    }

    @Test
    void productsOfAnotherStoreAreInvisible() throws Exception {
        perform(get("/api/products"), tokenB).andExpect(status().isOk())
                .andExpect(jsonPath("$..id", not(hasItem(productA))));
        perform(get("/api/products/" + productA), tokenB).andExpect(status().isNotFound());
        perform(put("/api/products/" + productA).content("""
                {"name": "Cambiado"}"""), tokenB).andExpect(status().isNotFound());
        perform(patch("/api/products/" + productA + "/stock").content("""
                {"delta": 5}"""), tokenB).andExpect(status().isNotFound());
        perform(get("/api/products/" + productA + "/stock-movements"), tokenB).andExpect(status().isNotFound());
    }

    @Test
    void zonesAndCarriersOfAnotherStoreAreInvisible() throws Exception {
        perform(get("/api/zones"), tokenB).andExpect(status().isOk()).andExpect(jsonPath("$..id", not(hasItem(zoneA))));
        perform(get("/api/zones/" + zoneA), tokenB).andExpect(status().isNotFound());
        perform(put("/api/zones/" + zoneA).content("""
                {"name": "Cambiada"}"""), tokenB).andExpect(status().isNotFound());

        perform(get("/api/carriers"), tokenB).andExpect(status().isOk())
                .andExpect(jsonPath("$..id", not(hasItem(carrierA))));
        perform(put("/api/carriers/" + carrierA).content("""
                {"name": "Cambiado", "type": "OWN"}"""), tokenB).andExpect(status().isNotFound());
    }

    @Test
    void aggregatesOnlyCountTheOwnStore() throws Exception {
        perform(get("/api/orders/agenda-summary").param("today", TODAY.toString()), tokenA)
                .andExpect(status().isOk()).andExpect(jsonPath("$.today").value(1));
        perform(get("/api/orders/agenda-summary").param("today", TODAY.toString()), tokenB)
                .andExpect(status().isOk()).andExpect(jsonPath("$.today").value(0));
    }

    @Test
    void bulkUpdatesOnlyTouchTheOwnStore() {
        UUID product = UUID.fromString(productA);

        assertThat(asStore(storeB, () -> productRepository.adjustStock(product, 5))).isZero();
        assertThat(asStore(storeB, () -> orderItemRepository.fillMissingUnitCost(product, BigDecimal.ONE))).isZero();
        assertThat(asStore(storeB, () -> customerAddressRepository.unsetOtherDefaults(
                UUID.fromString(customerA), UUID.randomUUID()))).isZero();

        assertThat(asStore(storeA, () -> productRepository.adjustStock(product, 0))).isOne();
    }

    @Test
    void userWithTwoStoresWorksInTheChosenOne() throws Exception {
        perform(get("/api/users/me"), tokenMulti).andExpect(status().isOk())
                .andExpect(jsonPath("$.stores.length()").value(2))
                .andExpect(jsonPath("$.role").doesNotExist());
        perform(get("/api/users/me"), tokenMulti, storeB).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DELIVERY"));

        perform(get("/api/orders"), tokenMulti).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elegí una tienda"));
        perform(get("/api/orders/" + orderA), tokenMulti, storeA).andExpect(status().isOk());
        perform(get("/api/orders/" + orderA), tokenMulti, storeB).andExpect(status().isNotFound());

        perform(get("/api/dashboard"), tokenMulti, storeA).andExpect(status().isOk());
        perform(get("/api/dashboard"), tokenMulti, storeB).andExpect(status().isForbidden());
    }

    @Test
    void storeHeaderMustBeOneOfTheUsersStores() throws Exception {
        perform(get("/api/orders"), tokenB, storeA).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tenés acceso a esta tienda"));
        perform(get("/api/orders").header("X-Store-Id", "no-es-un-id"), tokenB).andExpect(status().isBadRequest());
    }

    @Test
    void userWithoutStoresOnlySeesTheProfile() throws Exception {
        perform(get("/api/users/me"), tokenWithoutStores).andExpect(status().isOk())
                .andExpect(jsonPath("$.stores.length()").value(0));
        perform(get("/api/orders"), tokenWithoutStores).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tenés tiendas asignadas"));
    }

    @Test
    void inactiveStoreIsNotAccessible() throws Exception {
        UUID store = createStore("Tienda cerrada", null);
        UUID user = createUser("cerrada@tiendas.com");
        addMember(store, user, "ADMIN");
        String token = login("cerrada@tiendas.com");
        perform(get("/api/orders"), token, store).andExpect(status().isOk());

        jdbc.update("UPDATE app.stores SET active = false WHERE id = ?", store);

        perform(get("/api/orders"), token, store).andExpect(status().isForbidden());
    }

    @Test
    void removingTheMembershipCutsAccessRightAway() throws Exception {
        UUID user = createUser("temporal@tienda-a.com");
        addMember(storeA, user, "OPERATOR");
        String token = login("temporal@tienda-a.com");
        perform(get("/api/orders"), token).andExpect(status().isOk());

        jdbc.update("DELETE FROM app.store_members WHERE user_id = ?", user);

        perform(get("/api/orders"), token).andExpect(status().isForbidden());
    }

    @Test
    void uniqueValuesAreUniqueWithinEachStore() throws Exception {
        perform(post("/api/customers").content("""
                {"fullName": "Cliente B", "phone": "0981111111"}"""), tokenB).andExpect(status().is2xxSuccessful());
        perform(post("/api/customers").content("""
                {"fullName": "Otro cliente B", "phone": "0981111111"}"""), tokenB).andExpect(status().isConflict());

        perform(post("/api/products").content(productJson("SKU-1")), tokenB).andExpect(status().is2xxSuccessful());
        perform(post("/api/zones").content("""
                {"name": "Centro"}"""), tokenB).andExpect(status().is2xxSuccessful());
    }

    @Test
    void carrierUserMustBeDeliveryInTheCurrentStore() throws Exception {
        perform(post("/api/carriers").content("""
                {"name": "Multi en B", "type": "OWN", "userId": "%s"}""".formatted(multiUser)), tokenB)
                .andExpect(status().is2xxSuccessful());
        perform(post("/api/carriers").content("""
                {"name": "Multi en A", "type": "OWN", "userId": "%s"}""".formatted(multiUser)), tokenA)
                .andExpect(status().isBadRequest());
        perform(post("/api/carriers").content("""
                {"name": "Beto en A", "type": "OWN", "userId": "%s"}""".formatted(userB)), tokenA)
                .andExpect(status().isNotFound());
    }

    @Test
    void shopifyWebhookGoesToTheStoreOfItsDomain() throws Exception {
        String payload = """
                {"id": 987654}""";

        mvc.perform(post("/api/webhooks/shopify/orders").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Shopify-Shop-Domain", "desconocida.myshopify.com"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/webhooks/shopify/orders").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/webhooks/shopify/orders").contentType(MediaType.APPLICATION_JSON).content(payload)
                        .header("X-Shopify-Shop-Domain", "tienda-a.myshopify.com"))
                .andExpect(status().isOk());

        UUID failureStore = jdbc.queryForObject(
                "SELECT store_id FROM app.shopify_webhook_failures WHERE shopify_order_id = '987654'", UUID.class);
        assertThat(failureStore).isEqualTo(storeA);
        perform(get("/api/shopify/webhook-failures"), tokenA).andExpect(jsonPath("$..shopifyOrderId", hasItem("987654")));
        perform(get("/api/shopify/webhook-failures"), tokenB)
                .andExpect(jsonPath("$..shopifyOrderId", not(hasItem("987654"))));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token, UUID store) throws Exception {
        return perform(request.header("X-Store-Id", store.toString()), token);
    }

    private String create(String token, String path, String body) throws Exception {
        String response = perform(post(path).content(body), token)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private UUID createStore(String name, String shopifyDomain) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app.stores (id, name, shopify_shop_domain, active, created_at) VALUES (?, ?, ?, true, now())",
                id, name, shopifyDomain);
        return id;
    }

    private UUID createUser(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app.users (id, active, created_at, email, full_name, password)
                VALUES (?, true, now(), ?, ?, ?)""", id, email, email, passwordEncoder.encode(PASSWORD));
        return id;
    }

    private void addMember(UUID store, UUID user, String role) {
        jdbc.update("INSERT INTO app.store_members (id, store_id, user_id, role, created_at) VALUES (?, ?, ?, ?, now())",
                UUID.randomUUID(), store, user, role);
    }

    private <T> T asStore(UUID store, Supplier<T> action) {
        StoreContext.set(store, null);
        try {
            return transactionTemplate.execute(status -> action.get());
        } finally {
            StoreContext.clear();
        }
    }

    private static String productJson(String sku) {
        return """
                {"name": "Producto %s", "purchasePrice": 1000, "salePrice": 2000, "unit": "UNID", "currency": "PYG",
                 "stock": 10, "shopifySku": "%s"}""".formatted(sku, sku);
    }
}

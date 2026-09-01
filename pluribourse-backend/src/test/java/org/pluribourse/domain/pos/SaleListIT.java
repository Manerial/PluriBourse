package org.pluribourse.domain.pos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.item.dto.ItemDto;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.SaleDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4.7 — the "sales list" screen endpoint ({@code GET /pos/sales} + {@code /pos/sales/cashiers}).
 * Four real sales are produced through the actual POS basket flow (scan + validate) by two distinct
 * cashiers so the cashier filter, cashier selector and default {@code soldAt DESC} order can be
 * asserted against genuine data. Structure mirrors {@code ItemCatalogIT}: one scenario read as a
 * story-board, {@code @Order}ed, data persisting between methods.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SaleListIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private MockHttpSession volunteer2Session;
    private MockHttpSession sellerSession;
    private Long editionId;
    private Long categoryId;
    private Long aliceId;

    // Alice is seller #1: her items get barcodes 0001-000N in creation order.
    private static final String KAPLA_BARCODE = "00010001";  // 5.00
    private static final String PUZZLE_BARCODE = "00010002";  // 3.00
    private static final String EXTRA_BARCODE = "00010003";   // 2.00
    private static final String GROS_BARCODE = "00010004";    // 12.00

    private Long sale1Id; // volunteer1, CASH, 5.00
    private Long sale2Id; // volunteer1, CARD, 3.00
    private Long sale3Id; // volunteer2, CHECK, 2.00
    private Long sale4Id; // volunteer2, CASH, 12.00 — most recent

    @org.junit.jupiter.api.BeforeAll
    void setUpSessions() throws Exception {
        adminSession = login("test_admin");
        volunteer1Session = login("volunteer1");
        volunteer2Session = login("volunteer2");
        sellerSession = login("seller1");
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void create_edition_with_category_and_advance_to_deposit() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Ventes 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(1))))))
                .andExpect(status().isOk())
                .andReturn();
        categoryId = objectMapper.readValue(categoriesResult.getResponse().getContentAsString(),
                new TypeReference<List<EditionCategoryDto>>() {
                }).get(0).id();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(2)
    void create_seller_and_items_then_open_sale_phase() throws Exception {
        SellerDto alicePayload = new SellerDto(null, "Alice", "Vendeuse", "alice.ventes@email.com", "0600000001");
        MvcResult aliceResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alicePayload)))
                .andExpect(status().isCreated())
                .andReturn();
        aliceId = objectMapper.readValue(aliceResult.getResponse().getContentAsString(), SellerDto.class).id();

        createItem("Kapla", "5.00");
        createItem("Puzzle", "3.00");
        createItem("Extra", "2.00");
        createItem("Gros jouet", "12.00");

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
    }

    private void createItem(String name, String price) throws Exception {
        CreateItemDto payload = new CreateItemDto(aliceId, categoryId, name, new BigDecimal(price), false, null);
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    void produce_four_real_sales_from_two_cashiers() throws Exception {
        sale1Id = makeSale(volunteer1Session, KAPLA_BARCODE, PaymentMethod.CASH);
        sale2Id = makeSale(volunteer1Session, PUZZLE_BARCODE, PaymentMethod.CARD);
        sale3Id = makeSale(volunteer2Session, EXTRA_BARCODE, PaymentMethod.CHECK);
        sale4Id = makeSale(volunteer2Session, GROS_BARCODE, PaymentMethod.CASH);

        assertThat(List.of(sale1Id, sale2Id, sale3Id, sale4Id)).doesNotHaveDuplicates();
    }

    /**
     * Runs one full POS transaction (fresh basket, scan one item, validate) and returns the new
     * Sale id. Sleeps 10 ms afterwards so consecutive sales get distinct {@code soldAt} values —
     * the default {@code soldAt DESC} order and the date-range filter both depend on that spread.
     */
    private Long makeSale(MockHttpSession session, String barcode, PaymentMethod method) throws Exception {
        MvcResult basketResult = mockMvc.perform(get("/api/pos/baskets/current").session(session))
                .andExpect(status().isOk())
                .andReturn();
        Long basketId = objectMapper.readValue(basketResult.getResponse().getContentAsString(), BasketDto.class).id();

        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk());

        MvcResult validateResult = mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(method, null))))
                .andExpect(status().isOk())
                .andReturn();
        Long saleId = objectMapper.readValue(validateResult.getResponse().getContentAsString(), SaleDto.class).id();
        Thread.sleep(10);
        return saleId;
    }

    @Test
    @Order(4)
    void lists_every_sale_of_the_active_edition_most_recent_first() throws Exception {
        mockMvc.perform(get("/api/pos/sales").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.content.length()").value(4))
                // default order = soldAt DESC → the last sale produced comes first
                .andExpect(jsonPath("$.page.content[0].id").value(sale4Id))
                .andExpect(jsonPath("$.page.content[3].id").value(sale1Id))
                .andExpect(jsonPath("$.page.content[0].cashier").value("volunteer2"))
                .andExpect(jsonPath("$.page.content[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.page.content[0].total").value(12.00))
                .andExpect(jsonPath("$.page.content[0].currency").isNotEmpty());
    }

    @Test
    @Order(5)
    void admin_can_reach_the_sales_list_too() throws Exception {
        mockMvc.perform(get("/api/pos/sales").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    @Order(6)
    void filter_by_cashier_returns_only_that_cashiers_sales() throws Exception {
        mockMvc.perform(get("/api/pos/sales").param("cashier", "volunteer2").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[*].cashier", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("volunteer2"))));

        // AC 16 — a second request with a different filter, back to back, still gets its own
        // correct result: no shared server-side filter state.
        mockMvc.perform(get("/api/pos/sales").param("cashier", "volunteer1").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[*].cashier", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("volunteer1"))));
    }

    @Test
    @Order(7)
    void filter_by_date_range_includes_both_bounds() throws Exception {
        MvcResult all = mockMvc.perform(get("/api/pos/sales").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = objectMapper.readTree(all.getResponse().getContentAsString()).get("page").get("content");
        String latestSoldAt = content.get(0).get("soldAt").asText();   // sale4
        String earliestSoldAt = content.get(3).get("soldAt").asText(); // sale1

        // Lower bound inclusive: dateFrom == the earliest sale's soldAt still returns all 4.
        mockMvc.perform(get("/api/pos/sales").param("dateFrom", earliestSoldAt).session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));

        // Upper bound inclusive: dateTo == the latest sale's soldAt still returns all 4.
        mockMvc.perform(get("/api/pos/sales").param("dateTo", latestSoldAt).session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));

        // Both bounds pinned to the exact same instant (the latest sale) → that sale is returned,
        // proving [du, au] is closed on both ends simultaneously.
        mockMvc.perform(get("/api/pos/sales")
                        .param("dateFrom", latestSoldAt).param("dateTo", latestSoldAt)
                        .session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].id").value(sale4Id))
                .andExpect(jsonPath("$.page.totalElements", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // An empty bound = no limit on that side.
        mockMvc.perform(get("/api/pos/sales").param("dateTo", "2000-01-01T00:00:00").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/api/pos/sales").param("dateFrom", "2999-01-01T00:00:00").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));

        // AC 11 / Dev Notes — <input type="datetime-local"> submits minute precision with NO
        // seconds ("2999-01-01T00:00"); Spring's @DateTimeFormat(iso = DATE_TIME) must accept that
        // exact wire format, not only the full "...T00:00:00" form.
        mockMvc.perform(get("/api/pos/sales").param("dateFrom", "2999-01-01T00:00").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/api/pos/sales").param("dateTo", "2000-01-01T00:00").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @Order(8)
    void sort_by_total_toggles_between_ascending_and_descending() throws Exception {
        // JPageFlow 1.7.0 sorts BigDecimal numerically (ARCH-005 fixed, cf. story 6.1).
        mockMvc.perform(get("/api/pos/sales").param("sort", "total,asc").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].total").value(2.00))
                .andExpect(jsonPath("$.page.content[3].total").value(12.00));

        mockMvc.perform(get("/api/pos/sales").param("sort", "total,desc").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].total").value(12.00))
                .andExpect(jsonPath("$.page.content[3].total").value(2.00));
    }

    @Test
    @Order(9)
    void sort_by_cashier_username() throws Exception {
        mockMvc.perform(get("/api/pos/sales").param("sort", "user.username,asc").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].cashier").value("volunteer1"))
                .andExpect(jsonPath("$.page.content[3].cashier").value("volunteer2"));
    }

    @Test
    @Order(10)
    void sort_by_payment_method_is_handled_without_error() throws Exception {
        // enum sort — Comparable, ordered by declaration (CASH < CHECK < CARD). Only asserts
        // JPageFlow 1.7.0 accepts an enum sort field (no exception) and the ends are right.
        mockMvc.perform(get("/api/pos/sales").param("sort", "paymentMethod,asc").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.page.content[3].paymentMethod").value("CARD"));
    }

    @Test
    @Order(11)
    void unknown_sort_field_returns_400() throws Exception {
        mockMvc.perform(get("/api/pos/sales").param("sort", "password,asc").session(volunteer1Session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-sort-field")));
    }

    @Test
    @Order(12)
    void out_of_bounds_paging_params_return_422() throws Exception {
        mockMvc.perform(get("/api/pos/sales").param("page", "-1").session(volunteer1Session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/validation-failed")));
        mockMvc.perform(get("/api/pos/sales").param("size", "999").session(volunteer1Session))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(endsWith("/validation-failed")));
    }

    @Test
    @Order(13)
    void page_beyond_last_page_is_clamped_and_reports_correct_total() throws Exception {
        mockMvc.perform(get("/api/pos/sales").param("page", "10").param("size", "2").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.content.length()").value(2));
    }

    @Test
    @Order(14)
    void cashiers_endpoint_lists_distinct_usernames_sorted() throws Exception {
        mockMvc.perform(get("/api/pos/sales/cashiers").session(volunteer1Session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("volunteer1"))
                .andExpect(jsonPath("$[1]").value("volunteer2"));
    }

    @Test
    @Order(15)
    void seller_role_cannot_access_the_sales_list() throws Exception {
        mockMvc.perform(get("/api/pos/sales").session(sellerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/pos/sales/cashiers").session(sellerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(16)
    void sales_list_unavailable_once_no_edition_is_active() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        mockMvc.perform(get("/api/pos/sales").session(volunteer1Session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/no-active-edition")));
    }
}

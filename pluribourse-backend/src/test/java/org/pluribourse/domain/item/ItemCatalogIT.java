package org.pluribourse.domain.item;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.domain.edition.dto.*;
import org.pluribourse.domain.item.dto.*;
import org.pluribourse.domain.seller.dto.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sellers are created in a fixed order (Alice then Bruno) so their edition-scoped seller numbers
 * (1 then 2) are deterministic — several assertions below (barcode partial-match, table-number
 * exact-match regression guard) depend on knowing the exact barcode/table each seeded item gets.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ItemCatalogIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private MockHttpSession sellerSession;
    private Long editionId;
    private Long jouetsCategoryId;
    private Long livresCategoryId;
    private Long sellerAliceId;
    private Long sellerBrunoId;

    @BeforeAll
    void setUpSessions() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);

        MvcResult sellerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "seller1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        sellerSession = (MockHttpSession) sellerLogin.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void create_edition_with_categories_and_advance_to_deposit() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Catalogue 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        List<EditionCategoryDto> categoriesPayload = List.of(
                new EditionCategoryDto(null, "Jouets", List.of(1)),
                new EditionCategoryDto(null, "Livres", List.of(2)),
                // Distinct table (11) sharing digits with table 1 — regression guard (AC2/AC6:
                // exact-match filtering must not treat "1" as a substring of "11").
                new EditionCategoryDto(null, "Jeux", List.of(11))
        );
        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoriesPayload)))
                .andExpect(status().isOk())
                .andReturn();
        List<EditionCategoryDto> categories = objectMapper.readValue(
                categoriesResult.getResponse().getContentAsString(), new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        jouetsCategoryId = categories.get(0).id();
        livresCategoryId = categories.get(1).id();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));
    }

    @Test
    @Order(2)
    void create_sellers() throws Exception {
        sellerAliceId = createSeller("Alice", "Vendeuse", "alice.catalogue@email.com");
        sellerBrunoId = createSeller("Bruno", "Vendeur", "bruno.catalogue@email.com");
    }

    @Test
    @Order(3)
    void register_items_across_sellers_and_categories() throws Exception {
        createItem(sellerAliceId, jouetsCategoryId, "Kapla", "5.00", false, null);
        createItem(sellerAliceId, livresCategoryId, "Robot incomplet", "8.00", true, "Piece manquante");
        Long jeuxCategoryId = getCategoryIdByName("Jeux");
        createItem(sellerBrunoId, jeuxCategoryId, "Console", "50.00", false, null);
    }

    @Test
    @Order(4)
    void volunteer_lists_all_catalog_items_with_no_filter() throws Exception {
        mockMvc.perform(get("/api/catalog").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.content.length()").value(3));
    }

    @Test
    @Order(5)
    void admin_lists_all_catalog_items_with_no_filter() throws Exception {
        // AC1 names both ADMIN and VOLUNTEER — covered above only for volunteer until now.
        mockMvc.perform(get("/api/catalog").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.content.length()").value(3));
    }

    @Test
    @Order(6)
    void filter_by_category_returns_only_that_categorys_items() throws Exception {
        mockMvc.perform(get("/api/catalog").param("categoryId", String.valueOf(jouetsCategoryId)).session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Kapla"));
    }

    @Test
    @Order(7)
    void filter_by_table_number_does_not_match_table_sharing_the_same_digit() throws Exception {
        mockMvc.perform(get("/api/catalog").param("tableNumber", "1").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Kapla"));
    }

    @Test
    @Order(8)
    void filter_by_incomplete_returns_only_incomplete_item() throws Exception {
        mockMvc.perform(get("/api/catalog").param("incomplete", "true").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Robot incomplet"));
    }

    @Test
    @Order(9)
    void filter_by_seller_name_is_partial_and_case_insensitive() throws Exception {
        mockMvc.perform(get("/api/catalog").param("sellerName", "vendeuse").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[*].sellerLastName", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Vendeuse"))));
    }

    @Test
    @Order(10)
    void filter_by_barcode_partial_digits_matches_computed_field() throws Exception {
        // Alice (seller #1) items get barcodes 0001-0001 (Kapla) and 0001-0002 (Robot incomplet);
        // Bruno (seller #2) gets 0002-0001 (Console). "10002" only occurs inside 0001-0002's digits.
        mockMvc.perform(get("/api/catalog").param("barcode", "10002").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Robot incomplet"));
    }

    @Test
    @Order(11)
    void sort_by_name_toggles_between_ascending_and_descending() throws Exception {
        mockMvc.perform(get("/api/catalog").param("sort", "name,asc").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].name").value("Console"))
                .andExpect(jsonPath("$.page.content[1].name").value("Kapla"))
                .andExpect(jsonPath("$.page.content[2].name").value("Robot incomplet"));

        mockMvc.perform(get("/api/catalog").param("sort", "name,desc").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].name").value("Robot incomplet"))
                .andExpect(jsonPath("$.page.content[1].name").value("Kapla"))
                .andExpect(jsonPath("$.page.content[2].name").value("Console"));
    }

    @Test
    @Order(12)
    // ARCH-005 fixed in JPageFlow 1.7.0: FilterService.compare() now falls back to Comparable.compareTo()
    // instead of toString().compareTo(), so BigDecimal (and other Comparable types) sort numerically.
    void sort_by_price_descending() throws Exception {
        mockMvc.perform(get("/api/catalog").param("sort", "price,desc").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].price").value(50.00))
                .andExpect(jsonPath("$.page.content[1].price").value(8.00))
                .andExpect(jsonPath("$.page.content[2].price").value(5.00));
    }

    @Test
    @Order(13)
    void filter_by_barcode_with_no_digits_matches_nothing() throws Exception {
        // Regression: queryDigitsOnly used to become "" for a letters-only query, and "".contains()
        // is always true, silently matching every item instead of none.
        mockMvc.perform(get("/api/catalog").param("barcode", "abc").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    @Order(14)
    void negative_page_returns_422() throws Exception {
        mockMvc.perform(get("/api/catalog").param("page", "-1").session(volunteerSession))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/validation-failed")));
    }

    @Test
    @Order(15)
    void size_out_of_bounds_returns_422() throws Exception {
        mockMvc.perform(get("/api/catalog").param("size", "0").session(volunteerSession))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/validation-failed")));
        mockMvc.perform(get("/api/catalog").param("size", "201").session(volunteerSession))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/validation-failed")));
    }

    @Test
    @Order(16)
    void page_beyond_last_page_is_clamped_and_reports_correct_total() throws Exception {
        // Regression: JPageFlow's FilterService returns Page.empty() (totalElements included) once
        // the requested page's slice is empty, silently discarding the real count. Clamping the
        // requested page keeps totalElements accurate and returns the last page's content instead.
        mockMvc.perform(get("/api/catalog").param("page", "10").param("size", "1").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.number").value(2))
                .andExpect(jsonPath("$.page.content.length()").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Console"));
    }

    @Test
    @Order(17)
    void unknown_sort_field_returns_400() throws Exception {
        mockMvc.perform(get("/api/catalog").param("sort", "password,asc").session(volunteerSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-sort-field")));
    }

    @Test
    @Order(18)
    void catalog_unavailable_once_edition_is_closed() throws Exception {
        // Catalog is scoped to the currently ACTIVE edition only (PREPARATION/DEPOSIT/SALE/POST_SALE) —
        // browsing a closed or archived edition's catalog is out of scope for this story (a future
        // story will cover historical/past-edition catalog browsing).
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));
        // POST_SALE → CLOSED only via the dedicated /close endpoint (FR-096 follow-up fix).
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        mockMvc.perform(get("/api/catalog").session(volunteerSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/no-active-edition")));
    }

    @Test
    @Order(19)
    void seller_role_cannot_access_catalog() throws Exception {
        mockMvc.perform(get("/api/catalog").session(sellerSession))
                .andExpect(status().isForbidden());
    }

    private Long createSeller(String firstName, String lastName, String email) throws Exception {
        SellerDto payload = new SellerDto(null, firstName, lastName, email, "0600000000");
        MvcResult result = mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SellerDto.class).id();
    }

    private void createItem(Long sellerProfileId, Long categoryId, String name, String price, boolean incomplete, String comment) throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerProfileId, categoryId, name, new java.math.BigDecimal(price), incomplete, comment);
        mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    private Long getCategoryIdByName(String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/categories").session(volunteerSession))
                .andExpect(status().isOk())
                .andReturn();
        List<EditionCategoryDto> categories = objectMapper.readValue(
                result.getResponse().getContentAsString(), new com.fasterxml.jackson.core.type.TypeReference<>() {
                });
        return categories.stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }
}

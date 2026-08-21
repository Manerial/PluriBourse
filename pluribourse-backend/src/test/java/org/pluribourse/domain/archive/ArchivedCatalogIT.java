package org.pluribourse.domain.archive;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.item.dto.CreateLotDto;
import org.pluribourse.domain.item.dto.CreateLotItemDto;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.domain.user.enums.Language;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 6.2 (AC 1-5, FR-088/FR-102): admin-only consultation of an archived edition's catalog.
 * The main edition ends up with 4 archived items across 2 categories, a mix of sold/unsold, and one
 * lot archived member-by-member (Duo A sold, Duo B not) — exercising every filter/sort dimension.
 * A second, distinct archived edition guards against cross-edition leakage, and a fresh
 * never-archived edition proves the endpoint doesn't 404 just because {@code archived_items} is empty.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ArchivedCatalogIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;
    private Long editionId;
    private Long secondEditionId;
    private Long freshEditionId;

    @Test
    @Order(1)
    void log_in_admin_and_volunteer() throws Exception {
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
    }

    @Test
    @Order(2)
    void build_and_archive_the_main_edition() throws Exception {
        editionId = createEdition("Bourse Catalogue Archivee 2026", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        List<EditionCategoryDto> categories = putCategories(editionId, List.of(
                new EditionCategoryDto(null, "Jouets", List.of(1)),
                new EditionCategoryDto(null, "Livres", List.of(2))
        ));
        Long jouetsId = categories.get(0).id();
        Long livresId = categories.get(1).id();

        advancePhase(editionId, "DEPOSIT");

        Long aliceId = createSeller("Alice", "Vendeuse", "alice.6-2@email.com", "0600000001");
        Long bobId = createSeller("Bob", "Vendeur", "bob.6-2@email.com", "0600000002");

        createItem(aliceId, jouetsId, "Kapla"); // sold
        createItem(aliceId, livresId, "Robot"); // unsold

        mockMvc.perform(post("/api/lots")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateLotDto(bobId, "Lot Duo", new BigDecimal("8.00"),
                                List.of(new CreateLotItemDto(jouetsId, "Duo A", false, null),
                                        new CreateLotItemDto(jouetsId, "Duo B", false, null))))))
                .andExpect(status().isCreated());

        advancePhase(editionId, "SALE");

        // Seller order Alice(1) then Bob(2): Kapla=0001-0001, Robot=0001-0002, Duo A=0002-0001.
        sellOneItem(volunteerSession, "00010001", PaymentMethod.CASH, "10.00"); // Kapla -> sold
        sellOneItem(volunteerSession, "00020001", PaymentMethod.CARD, "8.00"); // Duo A -> sold

        advancePhase(editionId, "POST_SALE");
        advancePhase(editionId, "CLOSED");

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/archive")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    @Order(3)
    void admin_lists_all_archived_items_with_no_filter_and_dto_shape_is_limited() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.content.length()").value(4))
                .andExpect(jsonPath("$.page.content[0].name").exists())
                .andExpect(jsonPath("$.page.content[0].categoryName").exists())
                .andExpect(jsonPath("$.page.content[0].sold").exists())
                .andExpect(jsonPath("$.page.content[0].barcode").doesNotExist())
                .andExpect(jsonPath("$.page.content[0].tableNumber").doesNotExist())
                .andExpect(jsonPath("$.page.content[0].price").doesNotExist())
                .andExpect(jsonPath("$.page.content[0].sellerLastName").doesNotExist())
                .andExpect(jsonPath("$.page.content[0].lotId").doesNotExist());
    }

    @Test
    @Order(4)
    void filter_by_name_is_partial_and_case_insensitive() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("name", "duo").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[*].name", everyItem(org.hamcrest.Matchers.startsWith("Duo"))));
    }

    @Test
    @Order(5)
    void filter_by_category_name_is_an_exact_match() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("categoryName", "Jouets").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.content[*].categoryName", everyItem(is("Jouets"))));

        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("categoryName", "Livres").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Robot"));
    }

    @Test
    @Order(6)
    void filter_by_sold_status() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sold", "true").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[*].sold", everyItem(is(true))));

        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sold", "false").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[*].sold", everyItem(is(false))));
    }

    @Test
    @Order(7)
    void sort_by_name_toggles_between_ascending_and_descending() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sort", "name,asc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].name").value("Duo A"))
                .andExpect(jsonPath("$.page.content[1].name").value("Duo B"))
                .andExpect(jsonPath("$.page.content[2].name").value("Kapla"))
                .andExpect(jsonPath("$.page.content[3].name").value("Robot"));

        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sort", "name,desc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].name").value("Robot"))
                .andExpect(jsonPath("$.page.content[3].name").value("Duo A"));
    }

    @Test
    @Order(8)
    void sort_by_category_name() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sort", "categoryName,asc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].categoryName").value("Jouets"))
                .andExpect(jsonPath("$.page.content[3].categoryName").value("Livres"));

        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sort", "categoryName,desc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].categoryName").value("Livres"))
                .andExpect(jsonPath("$.page.content[3].categoryName").value("Jouets"));
    }

    @Test
    @Order(9)
    void sort_by_sold_uses_natural_boolean_order() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sort", "sold,asc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].sold").value(false))
                .andExpect(jsonPath("$.page.content[1].sold").value(false))
                .andExpect(jsonPath("$.page.content[2].sold").value(true))
                .andExpect(jsonPath("$.page.content[3].sold").value(true));

        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sort", "sold,desc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content[0].sold").value(true))
                .andExpect(jsonPath("$.page.content[1].sold").value(true))
                .andExpect(jsonPath("$.page.content[2].sold").value(false))
                .andExpect(jsonPath("$.page.content[3].sold").value(false));
    }

    @Test
    @Order(10)
    void unknown_sort_field_returns_400() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("sort", "sellerLastName,asc").session(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invalid-sort-field")));
    }

    @Test
    @Order(11)
    void pagination_across_two_pages_reports_correct_total() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("size", "3").param("page", "0")
                        .param("sort", "name,asc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.content.length()").value(3));

        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("size", "3").param("page", "1")
                        .param("sort", "name,asc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.content.length()").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Robot"));
    }

    @Test
    @Order(12)
    void combined_filters_compose_with_and_semantics() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("categoryName", "Jouets").param("sold", "true").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[*].name", org.hamcrest.Matchers.containsInAnyOrder("Kapla", "Duo A")));

        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("name", "duo").param("sold", "false").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Duo B"));
    }

    @Test
    @Order(13)
    void page_beyond_last_page_is_clamped_and_reports_correct_total() throws Exception {
        // Regression: JPageFlow's FilterService returns Page.empty() (totalElements included) once
        // the requested page's slice is empty, silently discarding the real count. Clamping the
        // requested page keeps totalElements accurate and returns the last page's content instead.
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("page", "10").param("size", "1")
                        .param("sort", "name,asc").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.number").value(3))
                .andExpect(jsonPath("$.page.content.length()").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Robot"));
    }

    @Test
    @Order(14)
    void size_at_upper_bound_is_accepted_and_over_it_is_rejected() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("size", "200").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));

        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("size", "201").session(adminSession))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/validation-failed")));
    }

    @Test
    @Order(15)
    void empty_category_name_filter_behaves_like_no_filter() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items")
                        .param("categoryName", "").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    @Order(16)
    void volunteer_cannot_access_archived_catalog() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items").session(volunteerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(17)
    void second_archived_edition_only_returns_its_own_items() throws Exception {
        secondEditionId = createEdition("Bourse Catalogue Archivee Bis 2026", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3));
        List<EditionCategoryDto> categories = putCategories(secondEditionId, List.of(
                new EditionCategoryDto(null, "Vetements", List.of(1))
        ));
        Long vetementsId = categories.get(0).id();

        advancePhase(secondEditionId, "DEPOSIT");
        Long charlieId = createSeller("Charlie", "Vendeur", "charlie.6-2@email.com", "0600000003");
        createItem(charlieId, vetementsId, "Manteau");
        advancePhase(secondEditionId, "SALE");
        advancePhase(secondEditionId, "POST_SALE");
        advancePhase(secondEditionId, "CLOSED");

        mockMvc.perform(post("/api/admin/editions/" + secondEditionId + "/archive")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/archive/editions/" + secondEditionId + "/items").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].name").value("Manteau"));

        // Regression guard: the main edition's own listing is unaffected by the second edition existing.
        mockMvc.perform(get("/api/admin/archive/editions/" + editionId + "/items").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4));
    }

    @Test
    @Order(18)
    void edition_that_was_never_archived_returns_an_empty_page_not_404() throws Exception {
        freshEditionId = createEdition("Bourse Jamais Archivee 2026", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));

        mockMvc.perform(get("/api/admin/archive/editions/" + freshEditionId + "/items").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(0))
                .andExpect(jsonPath("$.page.content.length()").value(0));
    }

    @Test
    @Order(19)
    void unknown_edition_id_returns_404() throws Exception {
        mockMvc.perform(get("/api/admin/archive/editions/999999/items").session(adminSession))
                .andExpect(status().isNotFound());
    }

    private Long createEdition(String name, LocalDate startDate, LocalDate endDate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, name,
                                null, new BigDecimal("10.00"), Language.FR, null, false, startDate, endDate, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), EditionDto.class).id();
    }

    private List<EditionCategoryDto> putCategories(Long editionId, List<EditionCategoryDto> payload) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
    }

    private void advancePhase(Long editionId, String expectedPhase) throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value(expectedPhase));
    }

    private Long createSeller(String firstName, String lastName, String email, String phone) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sellers")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, firstName, lastName, email, phone))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), SellerDto.class).id();
    }

    private void createItem(Long sellerProfileId, Long categoryId, String name) throws Exception {
        mockMvc.perform(post("/api/items")
                        .session(volunteerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(sellerProfileId, categoryId, name, new BigDecimal("10.00"), false, null))))
                .andExpect(status().isCreated());
    }

    private void sellOneItem(MockHttpSession session, String barcode, PaymentMethod paymentMethod, String price) throws Exception {
        Long basketId = currentBasketId(session);
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(paymentMethod,
                                paymentMethod == PaymentMethod.CASH ? new BigDecimal(price) : null))))
                .andExpect(status().isOk());
    }

    private Long currentBasketId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/pos/baskets/current").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class).id();
    }
}

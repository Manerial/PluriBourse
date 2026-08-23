package org.pluribourse.domain.print;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.payout.dto.SettleDto;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.report.dto.EditionSummaryReportDto;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.domain.user.enums.Language;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 5.5: the edition summary report's two new money fields ({@code netPayoutTotal},
 * {@code associationRevenueTotal}) and the CSV exports (FR-091, FR-092), both admin-only and
 * reachable in Post-vente/Clôturée alike (same accessibility class as {@link EditionReportPrintingIT}).
 * Unlike that class, this one does not touch the print queue — CSV export is a plain synchronous
 * HTTP download — so it builds its own two-seller storyboard from scratch rather than extending
 * {@code EditionReportPrintingIT}'s single-seller (Bob, never settled) fixture.
 * <p>
 * Alice sells "Robe, rouge" (10.00€ CASH — the comma in the name proves CSV field escaping) and is
 * marked "Non réclamé" (FR-052): the association retains her full due amount, 9.00€ (10% commission).
 * Bob sells Kapla (20.00€ CASH) and keeps {@code Peluche "XL"} (5.00€, category "Jouets", table 7 —
 * the internal double quote proves the other half of RFC 4180 escaping) unsold; he is settled for
 * 10.00€ (FR-051, less than his 18.00€ due): the association retains the 8.00€ shortfall.
 * Edition totals: 2 sold items, 1 unsold, gross revenue 30.00€, commission 3.00€, netPayoutTotal
 * 27.00€ (= 9.00 + 18.00, the two sellers' full due amounts), associationRevenueTotal 20.00€
 * (= 3.00 commission + 9.00 Alice's full retained amount + 8.00 Bob's shortfall).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportExportIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EDITION_NAME = "Bourse Export Test";
    private static final String ALICE_ITEM_NAME = "Robe, rouge";
    private static final String BOB_UNSOLD_ITEM_NAME = "Peluche \"XL\"";

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private Long editionId;
    private Long categoryId;
    private Long aliceId;
    private Long bobId;

    @Test
    @Order(1)
    void set_up_sessions_and_create_edition_with_a_category_then_advance_to_deposit_phase() throws Exception {
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        MvcResult volunteer1Login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteer1Session = (MockHttpSession) volunteer1Login.getRequest().getSession(false);

        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, EDITION_NAME,
                                null, new BigDecimal("10.00"), Language.FR, null, false,
                                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(new EditionCategoryDto(null, "Jouets", List.of(7))))))
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
    void create_alice_and_bob_with_sold_and_unsold_items() throws Exception {
        MvcResult aliceResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Alice", "Dupont", "alice@email.com", "0600000001"))))
                .andExpect(status().isCreated())
                .andReturn();
        aliceId = objectMapper.readValue(aliceResult.getResponse().getContentAsString(), SellerDto.class).id();

        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(aliceId, categoryId, ALICE_ITEM_NAME, new BigDecimal("10.00"), false, null))))
                .andExpect(status().isCreated());

        MvcResult bobResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Bob", "Martin", "bob@email.com", "0600000002"))))
                .andExpect(status().isCreated())
                .andReturn();
        bobId = objectMapper.readValue(bobResult.getResponse().getContentAsString(), SellerDto.class).id();

        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(bobId, categoryId, "Kapla", new BigDecimal("20.00"), false, null))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateItemDto(bobId, categoryId, BOB_UNSOLD_ITEM_NAME, new BigDecimal("5.00"), false, null))))
                .andExpect(status().isCreated());
    }

    @Test
    @Order(3)
    void advance_to_sale_phase_and_sell_alices_and_bobs_first_items() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        // Alice's seller number 0001, first item -> barcode 00010001.
        sellItem("00010001", new BigDecimal("10.00"));
        // Bob's seller number 0002, first item (Kapla) -> barcode 00020001. BOB_UNSOLD_ITEM_NAME
        // (00020002) is never scanned and stays unsold.
        sellItem("00020001", new BigDecimal("20.00"));
    }

    @Test
    @Order(4)
    void exports_are_rejected_during_the_sale_phase() throws Exception {
        mockMvc.perform(get("/api/admin/reports/edition/" + editionId + "/export/catalog").session(adminSession))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-report-not-allowed")));
        mockMvc.perform(get("/api/admin/reports/edition/" + editionId + "/export/settlements").session(adminSession))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-report-not-allowed")));
    }

    @Test
    @Order(5)
    void advance_to_post_sale_and_settle_alice_as_unclaimed_and_bob_below_what_is_due() throws Exception {
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("POST_SALE"));

        mockMvc.perform(post("/api/settlements/" + aliceId + "/unclaimed")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNCLAIMED"));

        mockMvc.perform(post("/api/settlements/" + bobId + "/settle")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettleDto(new BigDecimal("10.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    @Order(6)
    void edition_report_reflects_net_payouts_and_association_retained_revenue() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports/edition/" + editionId).session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        EditionSummaryReportDto report = objectMapper.readValue(result.getResponse().getContentAsString(), EditionSummaryReportDto.class);

        assertThat(report.soldItemCount()).isEqualTo(2);
        assertThat(report.unsoldItemCount()).isEqualTo(1);
        assertThat(report.grossRevenue()).isEqualByComparingTo("30.00");
        assertThat(report.commission()).isEqualByComparingTo("3.00");
        // Alice's due (9.00) + Bob's due (18.00), independent of what each was actually paid.
        assertThat(report.netPayoutTotal()).isEqualByComparingTo("27.00");
        // Commission (3.00) + Alice's full retained amount (9.00, Non réclamé) + Bob's shortfall (18.00 - 10.00 = 8.00).
        assertThat(report.associationRevenueTotal()).isEqualByComparingTo("20.00");
    }

    @Test
    @Order(7)
    void catalog_export_is_a_utf8_bom_csv_with_localized_french_headers_and_escaped_fields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports/edition/" + editionId + "/export/catalog").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("catalogue.csv")))
                .andReturn();
        byte[] csv = result.getResponse().getContentAsByteArray();

        assertThat(csv[0]).isEqualTo((byte) 0xEF);
        assertThat(csv[1]).isEqualTo((byte) 0xBB);
        assertThat(csv[2]).isEqualTo((byte) 0xBF);

        assertCatalogExportContent(csv);
    }

    private void assertCatalogExportContent(byte[] csv) {
        String content = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertThat(content).startsWith("\"Nom\",\"Code-barres\",\"Catégorie\",\"Table\",\"Prix\",\"Statut\",\"Vendu\",\"Vendeur\"");
        // Field escaping (RFC 4180): the internal comma stays inside the quoted field, it does not
        // split into two CSV columns.
        assertThat(content).contains("\"" + ALICE_ITEM_NAME + "\",\"0001-0001\",\"Jouets\",\"7\",\"10.00\",\"Complet\",\"Vendu\",\"Alice Dupont\"");
        assertThat(content).contains("\"Kapla\",\"0002-0001\",\"Jouets\",\"7\",\"20.00\",\"Complet\",\"Vendu\",\"Bob Martin\"");
        // Field escaping (RFC 4180): the internal double quote is doubled, not left bare.
        assertThat(content).contains("\"Peluche \"\"XL\"\"\",\"0002-0002\",\"Jouets\",\"7\",\"5.00\",\"Complet\",\"Invendu\",\"Bob Martin\"");
    }

    @Test
    @Order(8)
    void settlements_export_has_one_row_per_settlement_status_with_the_full_amount_due() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/reports/edition/" + editionId + "/export/settlements").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("reversements.csv")))
                .andReturn();
        assertSettlementsExportContent(result.getResponse().getContentAsByteArray());
    }

    private void assertSettlementsExportContent(byte[] csv) {
        String content = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertThat(content).startsWith("\"Nom\",\"Prénom\",\"Téléphone\",\"Email\",\"Montant dû\",\"Statut\"");
        assertThat(content).contains("\"Dupont\",\"Alice\",\"0600000001\",\"alice@email.com\",\"9.00\",\"Non réclamé\"");
        assertThat(content).contains("\"Martin\",\"Bob\",\"0600000002\",\"bob@email.com\",\"18.00\",\"Soldé\"");
    }

    @Test
    @Order(9)
    void advancing_to_closed_leaves_both_exports_unchanged_and_still_resolvable_by_id() throws Exception {
        // Same reasoning as EditionReportPrintingIT Order 11: resolution by explicit edition ID
        // (EditionService.requireEdition, not getActiveEdition()) must keep these two endpoints
        // correct in Clôturée — nothing changes between Post-vente and Clôturée, no sale is
        // possible in the interval, and both sellers are already Soldé/Non réclamé so the
        // dedicated /close endpoint (FR-096 follow-up fix) has nothing left to auto-settle.
        mockMvc.perform(post("/api/admin/editions/" + editionId + "/close")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("CLOSED"));

        MvcResult catalogResult = mockMvc.perform(get("/api/admin/reports/edition/" + editionId + "/export/catalog").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        assertCatalogExportContent(catalogResult.getResponse().getContentAsByteArray());

        MvcResult settlementsResult = mockMvc.perform(get("/api/admin/reports/edition/" + editionId + "/export/settlements").session(adminSession))
                .andExpect(status().isOk())
                .andReturn();
        assertSettlementsExportContent(settlementsResult.getResponse().getContentAsByteArray());
    }

    @Test
    @Order(10)
    void volunteer_cannot_access_either_export_endpoint() throws Exception {
        mockMvc.perform(get("/api/admin/reports/edition/" + editionId + "/export/catalog").session(volunteer1Session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports/edition/" + editionId + "/export/settlements").session(volunteer1Session))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void export_endpoints_for_an_unknown_edition_id_return_404() throws Exception {
        mockMvc.perform(get("/api/admin/reports/edition/999999/export/catalog").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-not-found")));
        mockMvc.perform(get("/api/admin/reports/edition/999999/export/settlements").session(adminSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(endsWith("/edition-not-found")));
    }

    private void sellItem(String barcode, BigDecimal amountGiven) throws Exception {
        MvcResult basketResult = mockMvc.perform(get("/api/pos/baskets/current").session(volunteer1Session))
                .andExpect(status().isOk())
                .andReturn();
        Long basketId = objectMapper.readValue(basketResult.getResponse().getContentAsString(), BasketDto.class).id();

        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(volunteer1Session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/pos/baskets/" + basketId + "/validate")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidateBasketDto(PaymentMethod.CASH, amountGiven))))
                .andExpect(status().isOk());
    }
}

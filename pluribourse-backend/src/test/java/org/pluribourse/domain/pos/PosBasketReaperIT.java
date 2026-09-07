package org.pluribourse.domain.pos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.pluribourse.domain.edition.dto.EditionCategoryDto;
import org.pluribourse.domain.edition.dto.EditionDto;
import org.pluribourse.domain.item.dto.CreateItemDto;
import org.pluribourse.domain.item.dto.CreateLotDto;
import org.pluribourse.domain.item.dto.CreateLotItemDto;
import org.pluribourse.domain.item.repository.LotRepository;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.entity.Basket;
import org.pluribourse.domain.pos.repository.BasketItemRepository;
import org.pluribourse.domain.pos.repository.BasketRepository;
import org.pluribourse.domain.pos.service.BasketReaperService;
import org.pluribourse.domain.seller.dto.SellerDto;
import org.pluribourse.shared.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 4.9 (SCP 2026-09-04, review decision D2) — {@link BasketReaperService} cancels POS baskets
 * left on a silent terminal and releases their lot reservations, and an idle-expired session (FR-066)
 * is swept by the exact same path (there is no session-expiry listener).
 * <p>
 * A documented exception to the project's "E2E by the controllers" rule, on the same footing as
 * {@link SaleConcurrencyIT} (agreed at story 4.4 creation): the fixture is built through the HTTP
 * endpoints, but {@code last_seen_at} is then back-dated straight in the DB and the scheduled sweep
 * is invoked <strong>directly</strong> ({@code @Autowired BasketReaperService}) — a deterministic
 * "the terminal went silent N minutes ago" state cannot be produced through the controllers. The
 * {@code @Scheduled} trigger is off in the shared test profile ({@code pos.basket.reaper.enabled=false});
 * this class turns it back on for its own context so the bean exists, with a {@code PT1H} interval
 * <em>and</em> a {@code PT1H} initial delay (from the same property) so the scheduler never fires on
 * its own during the run — every sweep in this class is an explicit {@code reapInactiveBaskets()} call.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
        "pos.basket.reaper.enabled=true",
        "pos.basket.reaper.interval=PT1H",
        // Re-enabling the reaper forces a second application context (distinct from the shared one
        // every other IT uses). Give it its own in-memory database so its schema/data never bleeds
        // into — or from — a concurrently-cached context on the default jdbc:h2:mem:testdb URL.
        "spring.datasource.url=jdbc:h2:mem:reaper-testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE"
})
class PosBasketReaperIT extends IntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BasketRepository basketRepository;

    @Autowired
    private BasketItemRepository basketItemRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private BasketReaperService basketReaperService;

    @Autowired
    private ApplicationContext applicationContext;

    private static final String STANDALONE_A_BARCODE = "00010001";
    private static final String LOT_MEMBER_A_BARCODE = "00010002";
    private static final String LOT_MEMBER_B_BARCODE = "00010003";
    private static final String STANDALONE_B_BARCODE = "00010004";
    private static final String STANDALONE_C_BARCODE = "00010005";

    private MockHttpSession adminSession;
    private MockHttpSession volunteer1Session;
    private MockHttpSession volunteer2Session;
    private Long editionId;
    private Long lotId;
    private Long volunteer1BasketId;
    private Long volunteer2BasketId;

    @BeforeAll
    void setUpSessions() throws Exception {
        adminSession = login("test_admin", "Admin");
        volunteer1Session = login("volunteer1", "Admin");
        volunteer2Session = login("volunteer2", "Admin");
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @Order(1)
    void volunteer1_fills_a_basket_and_holds_a_lot_reservation() throws Exception {
        MvcResult editionResult = mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EditionDto(null, "Bourse Reaper 2026", null, null, null, null, false, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        editionId = objectMapper.readValue(editionResult.getResponse().getContentAsString(), EditionDto.class).id();

        List<EditionCategoryDto> categoriesPayload = List.of(new EditionCategoryDto(null, "Jouets", List.of(1)));
        MvcResult categoriesResult = mockMvc.perform(put("/api/admin/editions/" + editionId + "/categories")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(categoriesPayload)))
                .andExpect(status().isOk())
                .andReturn();
        Long categoryId = objectMapper.readValue(
                categoriesResult.getResponse().getContentAsString(), new TypeReference<List<EditionCategoryDto>>() {
                }).get(0).id();

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("DEPOSIT"));

        MvcResult sellerResult = mockMvc.perform(post("/api/sellers")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SellerDto(null, "Alice", "Vendeuse", "alice.reaper@email.com", "0600000001"))))
                .andExpect(status().isCreated())
                .andReturn();
        Long sellerId = objectMapper.readValue(sellerResult.getResponse().getContentAsString(), SellerDto.class).id();

        createItem(sellerId, categoryId, "Article A", "5.00");

        CreateLotDto lotPayload = new CreateLotDto(sellerId, categoryId, "Lot Reaper", new BigDecimal("6.00"),
                List.of(new CreateLotItemDto("Lot member A", false, null),
                        new CreateLotItemDto("Lot member B", false, null)));
        mockMvc.perform(post("/api/lots")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lotPayload)))
                .andExpect(status().isCreated());

        createItem(sellerId, categoryId, "Article B", "3.00");
        createItem(sellerId, categoryId, "Article C", "2.00");

        mockMvc.perform(post("/api/admin/editions/" + editionId + "/phase/advance")
                        .session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("SALE"));

        volunteer1BasketId = currentBasketId(volunteer1Session);
        addItem(volunteer1Session, volunteer1BasketId, STANDALONE_A_BARCODE);
        BasketDto afterLot = addItem(volunteer1Session, volunteer1BasketId, LOT_MEMBER_A_BARCODE);
        lotId = afterLot.lotGroups().get(0).lotId();

        assertThat(basketItemRepository.findAllByBasketIdOrderById(volunteer1BasketId)).hasSize(2);
        assertThat(lotRepository.findById(lotId).orElseThrow().getReservedByBasketId()).isEqualTo(volunteer1BasketId);
    }

    @Test
    @Order(2)
    void volunteer2_opens_a_fresh_basket_that_must_not_be_swept() throws Exception {
        volunteer2BasketId = currentBasketId(volunteer2Session);
        addItem(volunteer2Session, volunteer2BasketId, STANDALONE_B_BARCODE);
        assertThat(basketItemRepository.findAllByBasketIdOrderById(volunteer2BasketId)).hasSize(1);
    }

    @Test
    @Order(3)
    void volunteer1_terminal_goes_silent() {
        // Stands in for "tab closed / crash / network loss / idle-expired session": the heartbeat
        // (and every POS action) stopped refreshing last_seen_at long enough ago to cross the
        // dead-threshold (PT3M in the test profile).
        Basket basket = basketRepository.findById(volunteer1BasketId).orElseThrow();
        basket.setLastSeenAt(LocalDateTime.now().minusMinutes(10));
        basketRepository.saveAndFlush(basket);
    }

    @Test
    @Order(4)
    void the_reaper_cancels_the_silent_basket_and_releases_its_reservation() {
        basketReaperService.reapInactiveBaskets();

        assertThat(basketRepository.findById(volunteer1BasketId)).isEmpty();
        assertThat(basketItemRepository.findAllByBasketIdOrderById(volunteer1BasketId)).isEmpty();
        assertThat(lotRepository.findById(lotId).orElseThrow().getReservedByBasketId()).isNull();

        // volunteer2's basket has a recent last_seen_at — untouched.
        assertThat(basketRepository.findById(volunteer2BasketId)).isPresent();
        assertThat(basketItemRepository.findAllByBasketIdOrderById(volunteer2BasketId)).hasSize(1);
    }

    @Test
    @Order(5)
    void a_second_terminal_can_reserve_the_freed_lot() throws Exception {
        mockMvc.perform(post("/api/pos/baskets/" + volunteer2BasketId + "/items")
                        .session(volunteer2Session).with(csrf())
                        .param("barcode", LOT_MEMBER_A_BARCODE))
                .andExpect(status().isOk());
        assertThat(lotRepository.findById(lotId).orElseThrow().getReservedByBasketId()).isEqualTo(volunteer2BasketId);
    }

    @Test
    @Order(6)
    void a_basket_removed_before_the_sweep_is_simply_absent_from_its_worklist_and_the_others_still_reap() throws Exception {
        // volunteer2's basket is stale too, but a concurrent validate()/logout/phase change removes
        // it first (its FK ON DELETE SET NULL frees the lot it held in @Order(5)).
        backDate(volunteer2BasketId);
        basketRepository.deleteById(volunteer2BasketId);

        // A genuinely fresh stale basket the sweep must reap (volunteer1's earlier one was cancelled
        // in @Order(4)), carrying a reservation on the now-freed lot so the sweep's release path runs
        // on the survivor.
        Long v1FreshBasketId = currentBasketId(volunteer1Session);
        addItem(volunteer1Session, v1FreshBasketId, STANDALONE_C_BARCODE);
        BasketDto withLot = addItem(volunteer1Session, v1FreshBasketId, LOT_MEMBER_A_BARCODE);
        Long v1FreshLotId = withLot.lotGroups().get(0).lotId();
        assertThat(lotRepository.findById(v1FreshLotId).orElseThrow().getReservedByBasketId()).isEqualTo(v1FreshBasketId);
        backDate(v1FreshBasketId);

        // reapInactiveBaskets() runs findStale() fresh on every invocation, so the removed row is
        // never in the worklist — no per-basket failure here. The try/catch(RuntimeException) in the
        // loop is the belt-and-suspenders for a removal that lands *between* that query and the
        // cancelBasketSilently call; a black-box test cannot force that interleaving, so this asserts
        // the observable outcome: the sweep completes and the surviving stale basket reaps.
        assertThatCode(() -> basketReaperService.reapInactiveBaskets()).doesNotThrowAnyException();

        assertThat(basketRepository.findById(v1FreshBasketId)).isEmpty();
        assertThat(basketItemRepository.findAllByBasketIdOrderById(v1FreshBasketId)).isEmpty();
        assertThat(lotRepository.findById(v1FreshLotId).orElseThrow().getReservedByBasketId()).isNull();
        assertThat(basketRepository.findById(volunteer2BasketId)).isEmpty();
    }

    @Test
    @Order(7)
    void ac4_an_idle_expired_session_needs_no_dedicated_code_it_falls_through_the_time_based_sweep() {
        // AC4 is a "no code needed" criterion, substantively covered by @Order(3)-(4): an idle-expired
        // session (FR-066, 1 h) just stops refreshing last_seen_at and is swept like any silent
        // terminal. This method is its documentation anchor — the only thing it can assert is that
        // the sweep is wired as a single bean with no session-expiry listener alongside it.
        assertThat(applicationContext.getBeanNamesForType(BasketReaperService.class)).hasSize(1);
    }

    private void backDate(Long basketId) {
        Basket basket = basketRepository.findById(basketId).orElseThrow();
        basket.setLastSeenAt(LocalDateTime.now().minusMinutes(10));
        basketRepository.saveAndFlush(basket);
    }

    private Long currentBasketId(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/pos/baskets/current").session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class).id();
    }

    private BasketDto addItem(MockHttpSession session, Long basketId, String barcode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/pos/baskets/" + basketId + "/items")
                        .session(session).with(csrf())
                        .param("barcode", barcode))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), BasketDto.class);
    }

    private void createItem(Long sellerId, Long categoryId, String name, String price) throws Exception {
        CreateItemDto payload = new CreateItemDto(sellerId, categoryId, name, new BigDecimal(price), false, null);
        mockMvc.perform(post("/api/items")
                        .session(volunteer1Session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }
}

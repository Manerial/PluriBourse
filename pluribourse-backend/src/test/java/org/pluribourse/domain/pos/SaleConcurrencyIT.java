package org.pluribourse.domain.pos;

import org.junit.jupiter.api.Test;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.entity.EditionCategory;
import org.pluribourse.domain.edition.entity.PhaseType;
import org.pluribourse.domain.edition.repository.EditionCategoryRepository;
import org.pluribourse.domain.edition.repository.EditionRepository;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.entity.Lot;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.item.repository.LotRepository;
import org.pluribourse.domain.pos.dto.BasketDto;
import org.pluribourse.domain.pos.dto.ConflictingItemDto;
import org.pluribourse.domain.pos.dto.SaleDto;
import org.pluribourse.domain.pos.dto.ValidateBasketDto;
import org.pluribourse.domain.pos.entity.Basket;
import org.pluribourse.domain.pos.entity.BasketItem;
import org.pluribourse.domain.pos.entity.PaymentMethod;
import org.pluribourse.domain.pos.exception.BasketValidationConflictException;
import org.pluribourse.domain.pos.exception.LotReservedException;
import org.pluribourse.domain.pos.repository.BasketItemRepository;
import org.pluribourse.domain.pos.repository.BasketRepository;
import org.pluribourse.domain.pos.repository.SaleRepository;
import org.pluribourse.domain.pos.service.PosBasketService;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.pluribourse.domain.seller.repository.SellerRepository;
import org.pluribourse.domain.user.entities.User;
import org.pluribourse.domain.user.enums.Language;
import org.pluribourse.domain.user.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two real concurrent-write scenarios that {@link PosBasketIT}'s sequential HTTP scenarios can never
 * exercise (there the first call fully commits before the second begins):
 * <ul>
 *   <li>Story 4.4 (AC 3) — the per-{@code Item} {@code @Version} optimistic-lock branch of
 *       {@link PosBasketService#validate} (the {@code ObjectOptimisticLockingFailureException} /
 *       {@code SnapshotIsolationException} catch): two terminals validating the <em>same</em> item.</li>
 *   <li>Story 4.8 (FR-109) — the scan-time lot reservation ({@link PosBasketService#addItem}): two
 *       terminals adding <em>different</em> members of the same lot to their own baskets at the same
 *       time. Exactly one takes the reservation, the other gets {@link LotReservedException}; no
 *       {@code Sale} is created and no deadlock/lock-wait is possible (single-row {@code UPDATE}).
 *       The old force-increment of {@code Lot.@Version} at validation time is gone, so there is no
 *       longer a lot race to test in {@code validate()}.</li>
 * </ul>
 * <p>
 * Bypasses MockMvc/the controller deliberately (a second, technique-driven exception to the
 * project's E2E-by-controller testing philosophy, confirmed with the user at story creation):
 * producing a deterministic race between two transactions requires direct control over transaction
 * boundaries — two real threads, each driving its own {@link TransactionTemplate} — that a
 * sequential HTTP call cannot provide (architecture.md § Concurrence — POS).
 * <p>
 * Runs against a real MariaDB container (Testcontainers) rather than H2, whose optimistic-lock
 * semantics differ (architecture.md, same section) — skipped entirely (not failed) when Docker is
 * unavailable, since the project has no CI pipeline yet and this test is meant to run locally only.
 * {@code disabledWithoutDocker = true} is required for that clean skip: {@code @Testcontainers}
 * starts the static {@code @Container} field via its {@code BeforeAllCallback}, which JUnit 5 runs
 * before any user-defined {@code @BeforeAll} method — a manual {@code Assumptions.assumeTrue} guard
 * there would run too late and the container start would already have failed the whole class.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SaleConcurrencyIT {

    @Container
    @ServiceConnection
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11");

    @Autowired
    private PosBasketService posBasketService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EditionRepository editionRepository;

    @Autowired
    private EditionCategoryRepository editionCategoryRepository;

    @Autowired
    private SellerRepository sellerRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private LotRepository lotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BasketRepository basketRepository;

    @Autowired
    private BasketItemRepository basketItemRepository;

    @Autowired
    private SaleRepository saleRepository;

    /**
     * Each test commits its own edition-scoped fixtures (no enclosing transaction — the background
     * threads must see them). With more than one test method in the class now, wipe the data
     * between tests so every {@code saleRepository.count()} assertion starts from zero. FK-safe
     * order; never touches the users seeded by {@code test-data.sql}.
     */
    @org.junit.jupiter.api.AfterEach
    void wipeFixtures() {
        basketItemRepository.deleteAll();
        basketRepository.deleteAll();
        itemRepository.deleteAll();
        saleRepository.deleteAll();
        lotRepository.deleteAll();
        editionCategoryRepository.deleteAll();
        sellerRepository.deleteAll();
        editionRepository.deleteAll();
    }

    @Test
    // Never wrap this method (or the class) in @Transactional: the fixtures below must actually
    // commit so the two background threads' own transactions can see them — an enclosing
    // transaction would make this data invisible to both, or deadlock against them instead.
    void two_concurrent_validations_on_the_same_item_exactly_one_succeeds() throws InterruptedException {
        Edition edition = new Edition();
        edition.setName("Bourse Concurrence");
        edition.setPhase(PhaseType.SALE);
        edition.setCommissionRate(new BigDecimal("10.00"));
        edition.setDocumentLanguage(Language.FR);
        edition.setCurrency("€");
        edition.setCreatedAt(LocalDate.now());
        edition.setStartDate(LocalDate.now());
        edition.setEndDate(LocalDate.now().plusDays(1));
        edition = editionRepository.save(edition);

        SellerProfile seller = new SellerProfile();
        seller.setEdition(edition);
        seller.setFirstName("Concurrence");
        seller.setLastName("Vendeuse");
        seller.setEmail("concurrence@example.com");
        seller.setPhone("0600000099");
        seller.setSellerNumber(1);
        seller = sellerRepository.save(seller);

        EditionCategory category = new EditionCategory();
        category.setEdition(edition);
        category.setName("Jouets");
        category = editionCategoryRepository.save(category);

        Item item = new Item();
        item.setEdition(edition);
        item.setSellerProfile(seller);
        item.setCategory(category);
        item.setName("Article disputé");
        item.setPrice(new BigDecimal("5.00"));
        item.setIncomplete(false);
        item.setSold(false);
        item.setTableNumber(1);
        item.setItemNumber(1);
        item = itemRepository.save(item);
        Long itemId = item.getId();

        Long volunteer1Id = userRepository.findByUsername("volunteer1").orElseThrow().getId();
        Long volunteer2Id = userRepository.findByUsername("volunteer2").orElseThrow().getId();

        Long basket1Id = createBasketWithItem(edition, volunteer1Id, item);
        Long basket2Id = createBasketWithItem(edition, volunteer2Id, item);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        ValidateBasketDto payload = new ValidateBasketDto(PaymentMethod.CASH, null);
        CountDownLatch startSignal = new CountDownLatch(1);

        int successCount = 0;
        SaleDto winningSale = null;
        BasketValidationConflictException conflict = null;
        // try-with-resources (ExecutorService is AutoCloseable since Java 19): guarantees the pool
        // is shut down even if future.get() below is itself interrupted, not just on the happy path.
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SaleDto> future1 = executor.submit(() -> {
                startSignal.await();
                return transactionTemplate.execute(status -> posBasketService.validate(basket1Id, payload, volunteer1Id));
            });
            Future<SaleDto> future2 = executor.submit(() -> {
                startSignal.await();
                return transactionTemplate.execute(status -> posBasketService.validate(basket2Id, payload, volunteer2Id));
            });
            startSignal.countDown();

            for (Future<SaleDto> future : List.of(future1, future2)) {
                try {
                    winningSale = future.get();
                    successCount++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BasketValidationConflictException.class);
                    conflict = (BasketValidationConflictException) e.getCause();
                }
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(winningSale).isNotNull();
        assertThat(conflict).isNotNull();
        assertThat(conflict.getConflictingItems())
                .extracting(ConflictingItemDto::itemId)
                .containsExactly(itemId);

        Item soldItem = itemRepository.findById(itemId).orElseThrow();
        assertThat(soldItem.isSold()).isTrue();
        assertThat(soldItem.getSale()).isNotNull();
        assertThat(soldItem.getSale().getId()).isEqualTo(winningSale.id());
        assertThat(saleRepository.count()).isEqualTo(1);
    }

    @Test
    // Same no-@Transactional rule as above — the fixtures must actually commit so the two
    // background threads can see them.
    void two_concurrent_add_item_of_two_members_of_the_same_lot_exactly_one_reserves() throws InterruptedException {
        Edition edition = new Edition();
        edition.setName("Bourse Concurrence Lot");
        edition.setPhase(PhaseType.SALE);
        edition.setCommissionRate(new BigDecimal("10.00"));
        edition.setDocumentLanguage(Language.FR);
        edition.setCurrency("€");
        edition.setCreatedAt(LocalDate.now());
        edition.setStartDate(LocalDate.now());
        edition.setEndDate(LocalDate.now().plusDays(1));
        edition = editionRepository.save(edition);

        SellerProfile seller = new SellerProfile();
        seller.setEdition(edition);
        seller.setFirstName("Concurrence");
        seller.setLastName("Lot");
        seller.setEmail("concurrence.lot@example.com");
        seller.setPhone("0600000098");
        seller.setSellerNumber(1);
        seller = sellerRepository.save(seller);

        EditionCategory category = new EditionCategory();
        category.setEdition(edition);
        category.setName("Jouets");
        category = editionCategoryRepository.save(category);

        Lot lot = new Lot();
        lot.setEdition(edition);
        lot.setSellerProfile(seller);
        lot.setCategory(category);
        lot.setName("Lot disputé");
        lot.setGlobalPrice(new BigDecimal("12.00"));
        lot = lotRepository.save(lot);
        Long lotId = lot.getId();

        Item memberA = newLotMember(edition, seller, category, lot, "Membre A", 1);
        Item memberB = newLotMember(edition, seller, category, lot, "Membre B", 2);
        itemRepository.save(memberA);
        itemRepository.save(memberB);
        String memberABarcode = String.format("%04d%04d", 1, 1);
        String memberBBarcode = String.format("%04d%04d", 1, 2);

        Long volunteer1Id = userRepository.findByUsername("volunteer1").orElseThrow().getId();
        Long volunteer2Id = userRepository.findByUsername("volunteer2").orElseThrow().getId();

        // Empty baskets: the concurrent addItem itself is what takes the reservation.
        Long basket1Id = createEmptyBasket(edition, volunteer1Id);
        Long basket2Id = createEmptyBasket(edition, volunteer2Id);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch startSignal = new CountDownLatch(1);

        int successCount = 0;
        Throwable losingCause = null;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<BasketDto> future1 = executor.submit(() -> {
                startSignal.await();
                return transactionTemplate.execute(status -> posBasketService.addItem(basket1Id, memberABarcode, volunteer1Id));
            });
            Future<BasketDto> future2 = executor.submit(() -> {
                startSignal.await();
                return transactionTemplate.execute(status -> posBasketService.addItem(basket2Id, memberBBarcode, volunteer2Id));
            });
            startSignal.countDown();

            for (Future<BasketDto> future : List.of(future1, future2)) {
                try {
                    future.get();
                    successCount++;
                } catch (ExecutionException e) {
                    losingCause = e.getCause();
                }
            }
        }

        assertThat(successCount).as("exactly one addItem takes the reservation").isEqualTo(1);
        assertThat(losingCause).isInstanceOf(LotReservedException.class);
        assertThat(saleRepository.count()).as("no sale is created on the reservation path").isZero();

        Lot reservedLot = lotRepository.findById(lotId).orElseThrow();
        assertThat(reservedLot.getReservedByBasketId())
                .as("the winner holds the reservation")
                .isIn(basket1Id, basket2Id);
        assertThat(reservedLot.getReservedAt()).isNotNull();
    }

    private Item newLotMember(Edition edition, SellerProfile seller, EditionCategory category, Lot lot, String name, int itemNumber) {
        Item item = new Item();
        item.setEdition(edition);
        item.setSellerProfile(seller);
        item.setCategory(category);
        item.setLot(lot);
        item.setName(name);
        item.setPrice(null);
        item.setIncomplete(false);
        item.setSold(false);
        item.setTableNumber(1);
        item.setItemNumber(itemNumber);
        return item;
    }

    private Long createBasketWithItem(Edition edition, Long userId, Item item) {
        User user = userRepository.getReferenceById(userId);
        Basket basket = new Basket();
        basket.setEdition(edition);
        basket.setUser(user);
        basket = basketRepository.save(basket);

        BasketItem basketItem = new BasketItem();
        basketItem.setBasket(basket);
        basketItem.setItem(item);
        basketItemRepository.save(basketItem);

        return basket.getId();
    }

    private Long createEmptyBasket(Edition edition, Long userId) {
        Basket basket = new Basket();
        basket.setEdition(edition);
        basket.setUser(userRepository.getReferenceById(userId));
        return basketRepository.save(basket).getId();
    }
}

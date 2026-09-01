package org.pluribourse.domain.payout;

import org.junit.jupiter.api.Test;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.edition.entity.EditionCategory;
import org.pluribourse.domain.edition.entity.PhaseType;
import org.pluribourse.domain.edition.repository.EditionCategoryRepository;
import org.pluribourse.domain.edition.repository.EditionRepository;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.payout.dto.SettleDto;
import org.pluribourse.domain.payout.dto.SettlementDto;
import org.pluribourse.domain.payout.exception.SellerAlreadySettledException;
import org.pluribourse.domain.payout.repository.SettlementRepository;
import org.pluribourse.domain.payout.service.SettlementService;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.pluribourse.domain.seller.repository.SellerRepository;
import org.pluribourse.domain.user.enums.Language;
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
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Story 5.7 (AC 1 / AC 7) — proves the {@code uk_settlements_seller_profile} unique constraint
 * branch of {@link SettlementService} ({@code persistSettlement}'s
 * {@code DataIntegrityViolationException} catch → {@link SellerAlreadySettledException}) under a
 * real concurrent write, something {@code SettlementIT} @Order(5)'s sequential double-settle never
 * exercises: there the first HTTP call fully commits before the second begins, so only the
 * up-front {@code requireNotAlreadySettled} read check is ever hit.
 * <p>
 * Bypasses MockMvc/the controller deliberately — the same technique-driven exception to the
 * project's E2E-by-controller philosophy already taken by {@code SaleConcurrencyIT} (story 4.4):
 * a deterministic race between two transactions needs direct control over transaction boundaries
 * (two real threads, each driving its own {@link TransactionTemplate}) that a sequential HTTP
 * call cannot provide. The E2E behaviour coverage stays in {@code SettlementIT} /
 * {@code SettlementSyncIT}; this test only adds the concurrency proof.
 * <p>
 * A single test method, like {@code SaleConcurrencyIT}: the fixtures set the edition phase
 * directly to POST_SALE, bypassing {@code advancePhase}'s single-active-edition guard, so two
 * coexisting active editions would make {@code EditionService.getActiveEdition()} resolve the
 * wrong one. It runs two races back to back on two sellers of that one edition — settle vs
 * settle, then settle vs {@code markUnclaimed} — so AC1's "un settle (ou un markUnclaimed, ou un
 * mélange des deux)" is covered without a second edition. Runs against a real MariaDB container
 * (Testcontainers) rather than H2, whose constraint/lock semantics differ — skipped entirely
 * (not failed) when Docker is unavailable ({@code disabledWithoutDocker = true}).
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SettlementConcurrencyIT {

    @Container
    @ServiceConnection
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11");

    @Autowired
    private SettlementService settlementService;

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
    private SettlementRepository settlementRepository;

    @Test
    // Never wrap this method (or the class) in @Transactional: the fixtures below must actually
    // commit so the two background threads' own transactions can see them — an enclosing
    // transaction would make this data invisible to both, or deadlock against them instead.
    void concurrent_writes_on_one_seller_leave_exactly_one_settlement() throws InterruptedException {
        Edition edition = new Edition();
        edition.setName("Bourse Solde Concurrent");
        edition.setPhase(PhaseType.POST_SALE);
        edition.setCommissionRate(new BigDecimal("10.00"));
        edition.setDocumentLanguage(Language.FR);
        edition.setCurrency("€");
        edition.setCreatedAt(LocalDate.now());
        edition.setStartDate(LocalDate.now());
        edition.setEndDate(LocalDate.now().plusDays(1));
        edition = editionRepository.save(edition);
        Long editionId = edition.getId();

        EditionCategory category = new EditionCategory();
        category.setEdition(edition);
        category.setName("Jouets");
        category = editionCategoryRepository.save(category);

        // 5.00 gross - 10% commission = 4.50 net due for each seller; every amount settled below
        // stays within it. Two sellers in the one POST_SALE edition so both races below run
        // without ever creating a second active edition (which would make
        // EditionService.getActiveEdition() resolve the wrong one).
        Long settleRaceSellerId = createSellerWithOneSoldItem(edition, category, 1, "Solde", "Concurrente");
        Long mixedRaceSellerId = createSellerWithOneSoldItem(edition, category, 2, "Mixte", "Concurrente");

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // AC1 — two concurrent settle calls for the same seller.
        RaceOutcome settleVsSettle = runRace(transactionTemplate,
                () -> settlementService.settle(settleRaceSellerId, new SettleDto(new BigDecimal("4.50"))),
                () -> settlementService.settle(settleRaceSellerId, new SettleDto(new BigDecimal("3.00"))));

        // AC1 — the "mélange des deux" the acceptance criterion also names: settle racing markUnclaimed.
        RaceOutcome settleVsUnclaimed = runRace(transactionTemplate,
                () -> settlementService.settle(mixedRaceSellerId, new SettleDto(new BigDecimal("4.50"))),
                () -> settlementService.markUnclaimed(mixedRaceSellerId));

        assertThat(settleVsSettle.successCount).isEqualTo(1);
        assertThat(settleVsSettle.conflict).isNotNull();
        assertThat(settleVsUnclaimed.successCount).isEqualTo(1);
        assertThat(settleVsUnclaimed.conflict).isNotNull();

        assertThat(settlementRepository.findBySellerProfileId(settleRaceSellerId)).isPresent();
        assertThat(settlementRepository.findBySellerProfileId(mixedRaceSellerId)).isPresent();
        assertThat(settlementRepository.findAllBySellerProfileEditionId(editionId)).hasSize(2);
    }

    private Long createSellerWithOneSoldItem(Edition edition, EditionCategory category, int sellerNumber,
                                             String firstName, String lastName) {
        SellerProfile seller = new SellerProfile();
        seller.setEdition(edition);
        seller.setFirstName(firstName);
        seller.setLastName(lastName);
        seller.setEmail(sellerNumber + ".concurrente@example.com");
        seller.setPhone("060000009" + sellerNumber);
        seller.setSellerNumber(sellerNumber);
        seller = sellerRepository.save(seller);

        Item item = new Item();
        item.setEdition(edition);
        item.setSellerProfile(seller);
        item.setCategory(category);
        item.setName("Article vendu");
        item.setPrice(new BigDecimal("5.00"));
        item.setIncomplete(false);
        item.setSold(true);
        item.setTableNumber(1);
        item.setItemNumber(sellerNumber);
        itemRepository.save(item);

        return seller.getId();
    }

    /**
     * Runs {@code action1} and {@code action2} on two threads, each in its own transaction, released
     * together by a shared latch. Returns how many committed and the
     * {@link SellerAlreadySettledException} the loser raised — whether from the up-front
     * {@code requireNotAlreadySettled} read check or the {@code uk_settlements_seller_profile}
     * constraint catch; the interleaving decides which, and NFR-008 only requires the clean 409
     * either way.
     */
    private RaceOutcome runRace(TransactionTemplate transactionTemplate,
                                Supplier<SettlementDto> action1,
                                Supplier<SettlementDto> action2) throws InterruptedException {
        CountDownLatch startSignal = new CountDownLatch(1);
        RaceOutcome outcome = new RaceOutcome();
        // try-with-resources (ExecutorService is AutoCloseable since Java 19): guarantees the pool
        // is shut down even if future.get() below is itself interrupted, not just on the happy path.
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SettlementDto> future1 = executor.submit(() -> {
                startSignal.await();
                return transactionTemplate.execute(status -> action1.get());
            });
            Future<SettlementDto> future2 = executor.submit(() -> {
                startSignal.await();
                return transactionTemplate.execute(status -> action2.get());
            });
            startSignal.countDown();

            for (Future<SettlementDto> future : List.of(future1, future2)) {
                try {
                    future.get();
                    outcome.successCount++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(SellerAlreadySettledException.class);
                    outcome.conflict = (SellerAlreadySettledException) e.getCause();
                }
            }
        }
        return outcome;
    }

    private static final class RaceOutcome {
        private int successCount;
        private SellerAlreadySettledException conflict;
    }
}

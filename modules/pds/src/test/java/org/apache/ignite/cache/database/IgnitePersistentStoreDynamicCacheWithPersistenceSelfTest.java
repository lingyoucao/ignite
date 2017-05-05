package org.apache.ignite.cache.database;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheRebalanceMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.MemoryConfiguration;
import org.apache.ignite.configuration.MemoryPolicyConfiguration;
import org.apache.ignite.configuration.PersistenceConfiguration;
import org.apache.ignite.internal.processors.cache.database.GridCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.database.wal.FileWriteAheadLogManager;
import org.apache.ignite.internal.processors.database.IgniteDbDynamicCacheSelfTest;
import org.apache.ignite.internal.util.typedef.internal.U;

/**
 *
 */
public class IgnitePersistentStoreDynamicCacheWithPersistenceSelfTest extends IgniteDbDynamicCacheSelfTest {
    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        System.setProperty(FileWriteAheadLogManager.IGNITE_PDS_WAL_MODE, "LOG_ONLY");

        System.setProperty(GridCacheDatabaseSharedManager.IGNITE_PDS_CHECKPOINT_TEST_SKIP_SYNC, "true");

        super.beforeTest();
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        deleteWorkFiles();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        System.clearProperty(FileWriteAheadLogManager.IGNITE_PDS_WAL_MODE);

        System.clearProperty(GridCacheDatabaseSharedManager.IGNITE_PDS_CHECKPOINT_TEST_SKIP_SYNC);

        deleteWorkFiles();
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        MemoryConfiguration dbCfg = new MemoryConfiguration();

        MemoryPolicyConfiguration memPlcCfg = new MemoryPolicyConfiguration();

        memPlcCfg.setName("dfltMemPlc");
        memPlcCfg.setSize(200 * 1024 * 1024);

        dbCfg.setMemoryPolicies(memPlcCfg);
        dbCfg.setDefaultMemoryPolicyName("dfltMemPlc");

        cfg.setMemoryConfiguration(dbCfg);

        cfg.setPersistenceConfiguration(new PersistenceConfiguration());

        if ("client".equals(gridName))
            cfg.setClientMode(true);

        return cfg;
    }

    /**
     * @throws Exception If failed.
     */
    public void testRestartAndCreate() throws Exception {
        startGrids(3);

        Ignite ignite = ignite(0);

        CacheConfiguration ccfg1 = new CacheConfiguration();

        ccfg1.setName("cache1");
        ccfg1.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
        ccfg1.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        ccfg1.setRebalanceMode(CacheRebalanceMode.NONE);
        ccfg1.setAffinity(new RendezvousAffinityFunction(false, 32));

        CacheConfiguration ccfg2 = new CacheConfiguration();

        ccfg2.setName("cache2");
        ccfg2.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
        ccfg2.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        ccfg2.setRebalanceMode(CacheRebalanceMode.NONE);
        ccfg2.setAffinity(new RendezvousAffinityFunction(false, 32));

        ignite.createCache(ccfg1);
        ignite.createCache(ccfg2);

        int iterations = 20;

        long stopTime = U.currentTimeMillis() + 20_000;

        for (int k = 0; k < iterations && U.currentTimeMillis() < stopTime; k++) {
            log.info("Iteration: " + k);

            stopAllGrids();

            startGrids(3);

            ignite = ignite(0);

            ignite.getOrCreateCache(ccfg1);

            ignite.getOrCreateCache(ccfg2);

            ignite.destroyCache(ccfg2.getName());

            ignite.getOrCreateCache(ccfg2);

            ignite.destroyCache(ccfg1.getName());
        }
    }

    /**
     * @throws IgniteCheckedException If failed.
     */
    private void deleteWorkFiles() throws IgniteCheckedException {
        deleteRecursively(U.resolveWorkDirectory(U.defaultWorkDirectory(), "db", false));
    }
}
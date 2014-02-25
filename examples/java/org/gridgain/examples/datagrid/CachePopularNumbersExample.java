// @java.file.header

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.examples.datagrid;

import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.cache.query.*;
import org.gridgain.grid.dataload.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.product.*;

import java.util.*;

import static org.gridgain.grid.product.GridProductEdition.*;

/**
 * Real time popular numbers counter.
 * <p>
 * Remote nodes should always be started with configuration which includes cache
 * using following command: {@code 'ggstart.sh examples/config/example-cache-popularcounts.xml'}.
 * <p>
 * The counts are kept in cache on all remote nodes. Top {@code 10} counts from each node are
 * then grabbed to produce an overall top {@code 10} list within the grid.
 *
 * @author @java.author
 * @version @java.version
 */
@GridOnlyAvailableIn(DATA_GRID)
public class CachePopularNumbersExample {
    /** Cache name. */
    private static final String CACHE_NAME = "partitioned";
    //private static final String CACHE_NAME = "replicated";
    //private static final String CACHE_NAME = "local";

    /** Count of most popular numbers to retrieve from grid. */
    private static final int POPULAR_NUMBERS_CNT = 10;

    /** Random number generator. */
    private static final Random RAND = new Random();

    /** Range within which to generate numbers. */
    private static final int RANGE = 1000;

    /** Count of total numbers to generate. */
    private static final int CNT = 1000000;

    /**
     * Starts counting numbers.
     *
     * @param args Command line arguments.
     * @throws Exception If failed.
     */
    public static void main(String[] args) throws Exception {
        Timer popularNumbersQryTimer = new Timer("numbers-query-worker");

        try (Grid g = GridGain.start("examples/config/example-cache.xml")) {
            GridProjection prj = g.forCache(CACHE_NAME);

            if (prj.nodes().isEmpty()) {
                System.out.println("Grid does not have cache configured: " + CACHE_NAME);

                return;
            }

            TimerTask task = scheduleQuery(g, popularNumbersQryTimer, POPULAR_NUMBERS_CNT);

            streamData(g);

            // Force one more run to get final counts.
            task.run();

            popularNumbersQryTimer.cancel();

            // Clean up caches on all nodes after run.
            prj.compute().run(new Runnable() {
                @Override public void run() {
                    System.out.println("Clearing keys from cache: " + g.cache(CACHE_NAME).size());

                    g.cache(CACHE_NAME).clearAll();
                }
            }).get();
        }
    }

    /**
     * Populates cache in real time with numbers and keeps count for every number.
     *
     * @param g Grid.
     * @throws GridException If failed.
     */
    private static void streamData(final Grid g) throws GridException {
        try (GridDataLoader<Integer, Long> ldr = g.dataLoader(CACHE_NAME)) {
            // Set larger per-node buffer size since our state is relatively small.
            ldr.perNodeBufferSize(2048);

            ldr.updater(new IncrementingUpdater());

            for (int i = 0; i < CNT; i++)
                ldr.addData(RAND.nextInt(RANGE), 1L);
        }
    }

    /**
     * Schedules our popular numbers query to run every 3 seconds.
     *
     * @param g Grid.
     * @param timer Timer.
     * @param cnt Number of popular numbers to return.
     * @return Scheduled task.
     */
    private static TimerTask scheduleQuery(final Grid g, Timer timer, final int cnt) {
        TimerTask task = new TimerTask() {
            private GridCacheFieldsQuery qry;

            @Override public void run() {
                // Get reference to cache.
                GridCache<Integer, Long> cache = g.cache(CACHE_NAME);

                if (qry == null)
                    qry = cache.queries().
                        createFieldsQuery("select _key, _val from Long order by _val desc limit " + cnt);

                try {
                    List<List<Object>> results = new ArrayList<>(qry.execute().get());

                    Collections.sort(results, new Comparator<List<Object>>() {
                        @Override public int compare(List<Object> r1, List<Object> r2) {
                            long cnt1 = (Long)r1.get(1);
                            long cnt2 = (Long)r2.get(1);

                            return cnt1 < cnt2 ? 1 : cnt1 > cnt2 ? -1 : 0;
                        }
                    });

                    for (int i = 0; i < cnt; i++) {
                        List<Object> res = results.get(i);

                        System.out.println(res.get(0) + "=" + res.get(1));
                    }

                    System.out.println("----------------");
                }
                catch (GridException e) {
                    e.printStackTrace();
                }
            }
        };

        timer.schedule(task, 3000, 3000);

        return task;
    }

    /**
     * Increments value for key.
     */
    private static class IncrementingUpdater implements GridDataLoadCacheUpdater<Integer, Long> {
        /** */
        private static final GridClosure<Long, Long> INC = new GridClosure<Long, Long>() {
            @Override public Long apply(Long e) {
                return e == null ? 1L : e + 1;
            }
        };

        /** {@inheritDoc} */
        @Override public void update(GridCache<Integer, Long> cache, Collection<Map.Entry<Integer, Long>> entries) throws GridException {
            for (Map.Entry<Integer, Long> entry : entries)
                cache.transform(entry.getKey(), INC);
        }
    }
}

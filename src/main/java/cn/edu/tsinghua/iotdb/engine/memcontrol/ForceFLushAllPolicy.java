package cn.edu.tsinghua.iotdb.engine.memcontrol;

import cn.edu.tsinghua.iotdb.concurrent.ThreadName;
import cn.edu.tsinghua.iotdb.engine.filenode.FileNodeManager;
import cn.edu.tsinghua.iotdb.utils.MemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForceFLushAllPolicy implements Policy {
    private static final Logger logger = LoggerFactory.getLogger(ForceFLushAllPolicy.class);
    private Thread workerThread;

    @Override
    public void execute() {
        logger.info("Memory reaches {}, current memory size is {}, JVM memory is {}, flushing.",
                BasicMemController.getInstance().getCurrLevel(),
                MemUtils.bytesCntToStr(BasicMemController.getInstance().getTotalUsage()),
                MemUtils.bytesCntToStr(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()));
        // use a thread to avoid blocking
        if (workerThread == null) {
            workerThread = createWorkerThread();
            workerThread.start();
        } else {
            if (workerThread.isAlive()) {
                logger.info("Last flush is ongoing...");
            } else {
                workerThread = createWorkerThread();
                workerThread.start();
            }
        }
    }

    private Thread createWorkerThread() {
        return new Thread(() -> {
            FileNodeManager.getInstance().forceFlush(BasicMemController.UsageLevel.DANGEROUS);
            System.gc();
        }, ThreadName.FORCE_FLUSH_ALL_POLICY.getName());
    }
}

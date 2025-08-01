/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.common.util;

import java.util.concurrent.*;
import org.jetbrains.annotations.NotNull;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;

public class TimedTaskManager {
  private static final Logger logger = new Logger(TimedTaskManager.class, LogGroup.General);

  private static class Singleton {
    public static final TimedTaskManager INSTANCE = new TimedTaskManager();
  }

  public static TimedTaskManager getInstance() {
    return Singleton.INSTANCE;
  }

  private static class CaughtThreadFactory implements ThreadFactory {
    private static final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

    @Override
    public Thread newThread(@NotNull Runnable r) {
      Thread thread = defaultThreadFactory.newThread(r);
      thread.setUncaughtExceptionHandler(
          (t, e) -> logger.error("TimedTask threw uncaught exception!", e));
      return thread;
    }
  }

  private static class CaughtScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
    public CaughtScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
      super(corePoolSize, threadFactory);
    }

    public Runnable wrap(Runnable runnable) {
      return () -> {
        try {
          runnable.run();
        } catch (Throwable t) {
          logger.error("Exception thrown by threadpool: " + t.getMessage(), t);
        }
      };
    }

    public <V> Callable<V> wrap(Callable<V> runnable) {
      return () -> {
        try {
          return runnable.call();
        } catch (Throwable t) {
          logger.error("Exception thrown by threadpool: " + t.getMessage(), t);
          return null;
        }
      };
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      return super.schedule(wrap(callable), delay, unit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      return super.schedule(wrap(command), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      return super.scheduleAtFixedRate(wrap(command), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return super.scheduleWithFixedDelay(wrap(command), initialDelay, delay, unit);
    }
  }

  private final CaughtScheduledThreadPoolExecutor timedTaskExecutorPool =
      new CaughtScheduledThreadPoolExecutor(2, new CaughtThreadFactory());
  private final ConcurrentHashMap<String, Future<?>> activeTasks = new ConcurrentHashMap<>();

  public void addTask(String identifier, Runnable runnable, long millisInterval) {
    if (!activeTasks.containsKey(identifier)) {
      var future =
          timedTaskExecutorPool.scheduleAtFixedRate(
              runnable, 0, millisInterval, TimeUnit.MILLISECONDS);
      activeTasks.put(identifier, future);
    }
  }

  public void addTask(
      String identifier, Runnable runnable, long millisStartDelay, long millisInterval) {
    if (!activeTasks.containsKey(identifier)) {
      var future =
          timedTaskExecutorPool.scheduleAtFixedRate(
              runnable, millisStartDelay, millisInterval, TimeUnit.MILLISECONDS);
      activeTasks.put(identifier, future);
    }
  }

  public void addOneShotTask(Runnable runnable, long millisStartDelay) {
    timedTaskExecutorPool.schedule(runnable, millisStartDelay, TimeUnit.MILLISECONDS);
  }

  public void cancelTask(String identifier) {
    var future = activeTasks.getOrDefault(identifier, null);
    if (future != null) {
      future.cancel(true);
      activeTasks.remove(identifier);
    }
  }

  public boolean taskActive(String identifier) {
    return activeTasks.containsKey(identifier);
  }
}

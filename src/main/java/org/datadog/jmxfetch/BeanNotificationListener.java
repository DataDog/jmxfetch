package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.management.MBeanServerNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;


@Slf4j
class BeanNotificationListener implements NotificationListener {
    private final BlockingQueue<MBeanServerNotification> queue;
    private final BeanTracker beanListener;

    public BeanNotificationListener(final BeanTracker bl) {
        this.beanListener = bl;
        this.queue = new LinkedBlockingQueue<>();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    MBeanServerNotification mbs;
                    try {
                        mbs = queue.take();
                        processMBeanServerNotification(mbs);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
            }
        }).start();
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        if (!(notification instanceof MBeanServerNotification)) {
            return;
        }
        MBeanServerNotification mbs = (MBeanServerNotification) notification;
        queue.offer(mbs);
    }

    private void processMBeanServerNotification(MBeanServerNotification notif) {
        log.debug("MBeanNotification: ts {} seqNum: {} msg: '{}'",
            notif.getTimeStamp(),
            notif.getSequenceNumber(),
            notif.getMessage());
        ObjectName beanName = notif.getMBeanName();
        if (notif.getType().equals(MBeanServerNotification.REGISTRATION_NOTIFICATION)) {
            beanListener.trackBean(beanName);
        } else if (notif.getType().equals(MBeanServerNotification.UNREGISTRATION_NOTIFICATION)) {
            beanListener.untrackBean(beanName);
        }
    }
}
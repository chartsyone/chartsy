package one.chartsy.persistence.event;

import javax.annotation.PostConstruct;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import javax.persistence.EntityManagerFactory;

public class HibernateEntityEventPublisher implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void registerListeners() {
        SessionFactoryImplementor sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        EventListenerRegistry listeners = sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);

        listeners.appendListeners(EventType.POST_COMMIT_INSERT, this);
        listeners.appendListeners(EventType.POST_COMMIT_UPDATE, this);
        listeners.appendListeners(EventType.POST_COMMIT_DELETE, this);
    }

    @Override
    public void onPostInsert(PostInsertEvent event) {
        eventPublisher.publishEvent(event.getEntity());
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        eventPublisher.publishEvent(event.getEntity());
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        eventPublisher.publishEvent(event.getEntity());
    }

    @Override
    public boolean requiresPostCommitHanding(EntityPersister persister) {
        return true;
    }
}

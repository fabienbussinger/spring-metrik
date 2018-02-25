package bzh.tibus29.spring.metrik.aop;

import bzh.tibus29.spring.metrik.MetrikContext;
import bzh.tibus29.spring.metrik.Metrik;
import bzh.tibus29.spring.metrik.MetrikHandler;
import bzh.tibus29.spring.metrik.MetrikWrapper;
import bzh.tibus29.spring.metrik.exception.MetrikException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Aspect
@SuppressWarnings("squid:S1181")
public class MetrikAspect {

    /** The Logger */
    private static final Logger log = LoggerFactory.getLogger(MetrikAspect.class);

    private final MetrikHandler handler;

    public MetrikAspect(MetrikHandler handler) {
        Assert.notNull(handler, "Handler is mandatory");
        this.handler = handler;
    }

    @Around("execution(@bzh.tibus29.spring.metrik.Metrik public * *(..)) && @annotation(metrikOnMethod)")
    public Object timedOnMethod(ProceedingJoinPoint pjp, Metrik metrikOnMethod) throws MetrikException {
        final Metrik metrikOnClass = AnnotationUtils.findAnnotation(pjp.getTarget().getClass(), Metrik.class);
        final MetrikWrapper wrapper = this.buildTimedWrapper(metrikOnClass, metrikOnMethod);
        return this.proceed(pjp, wrapper);
    }

    @Around("within(@bzh.tibus29.spring.metrik.Metrik *)")
    public Object timedOnClass(ProceedingJoinPoint pjp) throws MetrikException {
        final Metrik metrikOnClass = AnnotationUtils.findAnnotation(pjp.getTarget().getClass(), Metrik.class);
        final Metrik metrikOnMethod = AnnotationUtils.findAnnotation(this.getMethod(pjp), Metrik.class);

        if(metrikOnMethod == null) {
            return this.proceed(pjp, new MetrikWrapper(metrikOnClass));
        }

        try {
            return pjp.proceed();
        } catch (Throwable throwable) {
            throw new MetrikException(throwable);
        }
    }

    protected Object proceed(ProceedingJoinPoint pjp, MetrikWrapper timed) throws MetrikException {
        long t0 = current(timed.getMode());
        Object result = null;
        Throwable exception = null;

        try {
            result = pjp.proceed();
        } catch (Throwable throwable) {
            exception = throwable;
        }

        final long duration = current(timed.getMode()) - t0;
        final MethodSignature methodSignature = (MethodSignature) pjp.getSignature();

        if(timed.isDefaultValue()) {
            timed.setValue(pjp.getTarget().getClass().getSimpleName());
        }

        final MetrikContext context = new MetrikContext();
        context.setDuration(duration);
        context.setException(exception);
        context.setParams(this.params(methodSignature, pjp.getArgs()));
        context.setResult(result);
        context.setAnnotation(timed);
        context.setMethodName(this.getMethod(pjp).getName());

        try {
            this.handler.handleMetrik(context);
        } catch (Exception ex) {
            log.error("Something went wrong when handling metrik, cause : ", ex);
        }

        if(exception != null) {
            throw new MetrikException(exception);
        }

        return result;
    }

    protected MetrikWrapper buildTimedWrapper(Metrik metrikOnClass, Metrik metrikOnMethod) {

        if(metrikOnClass == null) {
            return new MetrikWrapper(metrikOnMethod);
        }

        final MetrikWrapper wrapper = new MetrikWrapper(metrikOnClass);

        if(!metrikOnMethod.value().equals("_")) {
            wrapper.setValue(metrikOnMethod.value());
        }

        if(metrikOnMethod.mode() == Metrik.Mode.NULL) {
            if(metrikOnClass.mode() != Metrik.Mode.NULL) {
                wrapper.setMethod(metrikOnClass.method());
            }
            else {
                wrapper.setMode(Metrik.Mode.MILLIS);
            }
        } else {
            wrapper.setMode(metrikOnMethod.mode());
        }

        wrapper.setEnabled(metrikOnMethod.enabled());

        return wrapper;
    }

    protected long current(Metrik.Mode mode) {
        return mode == Metrik.Mode.MILLIS ? System.currentTimeMillis() : System.nanoTime();
    }

    protected Map<String, Object> params(MethodSignature methodSignature, Object[] args) {

        final Map<String, Object> params = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            params.put(methodSignature.getParameterNames()[i], args[i]);
        }

        return params;
    }

    protected Method getMethod(ProceedingJoinPoint pjp) {
        return ((MethodSignature) pjp.getSignature()).getMethod();
    }
}

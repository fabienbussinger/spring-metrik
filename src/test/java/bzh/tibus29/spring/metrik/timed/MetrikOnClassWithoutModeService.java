package bzh.tibus29.spring.metrik.timed;

import bzh.tibus29.spring.metrik.Metrik;
import org.springframework.stereotype.Service;

@Metrik
@Service
public class MetrikOnClassWithoutModeService extends MetrikTestBusiness {

    @Metrik
    public void doSomething() {
        super.doSomething();
    }
}

package bean;

import java.util.List;

/**
 * @author malong
 * @date 2019/5/10
 */
public class ConfigLocalBean {
    private List<DependencyBean> libs;

    public List<DependencyBean> getLibs() {
        return libs;
    }

    public void setLibs(List<DependencyBean> libs) {
        this.libs = libs;
    }
}

package cn.iocoder.yudao.module.system.service.notify;

import cn.iocoder.yudao.module.system.api.notify.NotifySceneProvider;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySceneRespDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collection;

@Component
public class NotifySceneRegistry {

    private final Map<String, NotifySceneProvider> providers = new LinkedHashMap<>();
    private final Map<String, NotifySceneRespDTO> scenes = new LinkedHashMap<>();

    public NotifySceneRegistry(List<NotifySceneProvider> providerList) {
        for (NotifySceneProvider provider : providerList) {
            for (NotifySceneRespDTO scene : provider.getScenes()) {
                if (scenes.putIfAbsent(scene.getCode(), scene) != null) {
                    throw new IllegalStateException("Duplicate notify scene: " + scene.getCode());
                }
                providers.put(scene.getCode(), provider);
            }
        }
    }

    public List<NotifySceneRespDTO> getScenes() {
        return new ArrayList<>(scenes.values());
    }

    public NotifySceneRespDTO getScene(String sceneCode) {
        return scenes.get(sceneCode);
    }

    public NotifySceneProvider getProvider(String sceneCode) {
        return providers.get(sceneCode);
    }

    /** Returns template parameters that are not part of the current scene contract. */
    public Set<String> findInvalidTemplateParams(String sceneCode, Collection<String> params) {
        NotifySceneRespDTO scene = getScene(sceneCode);
        Set<String> available = new LinkedHashSet<>();
        if (scene != null && scene.getVariables() != null) {
            scene.getVariables().forEach(variable -> available.add(variable.getKey()));
        }
        Set<String> invalid = new LinkedHashSet<>();
        if (params != null) {
            params.stream().filter(param -> !available.contains(param)).forEach(invalid::add);
        }
        return invalid;
    }
}

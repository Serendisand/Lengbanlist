package org.leng.manager;

import org.leng.models.Model;
import org.leng.platform.PlatformHolder;

import java.util.HashMap;
import java.util.Map;

public class ModelManager {
    private static ModelManager instance;
    private static Map<String, Model> models = new HashMap<>();
    private static Model currentModel;
    private boolean enabled = true;

    public static ModelManager getInstance() {
        if (instance == null) {
            instance = new ModelManager();
        }
        return instance;
    }

    private ModelManager() {
        loadModel("Default");
        loadModel("English");
        loadModel("HuTao");
        loadModel("Furina");
        loadModel("Zhongli");
        loadModel("Keqing");
        loadModel("Xiao");
        loadModel("Ayaka");
        loadModel("Zero");
        loadModel("Herta");
        loadModel("Nahida");
        loadModel("Klee");
        loadModel("YaeMiko");

        String modelName = PlatformHolder.get().getConfigString("Model", "Default");
        switchModel(modelName.toLowerCase());
    }

    public static void loadModel(String modelName) {
        try {
            Class<?> modelClass = Class.forName("org.leng.models." + modelName);
            Model model = (Model) modelClass.getDeclaredConstructor().newInstance();
            models.put(modelName.toLowerCase(), model);
        } catch (Exception e) {
            PlatformHolder.get().logMessage("§c模型 " + modelName + " 加载失败！");
            e.printStackTrace();
        }
    }

    public static Model getCurrentModel() {
        return currentModel;
    }

    public static String getCurrentModelName() {
        return currentModel != null ? currentModel.getName() : "未知模型";
    }

    public static void switchModel(String modelName) {
        String lowerCaseModelName = modelName.toLowerCase();
        if (models.containsKey(lowerCaseModelName)) {
            currentModel = models.get(lowerCaseModelName);
            PlatformHolder.get().setConfigValue("Model", currentModel.getName());
            PlatformHolder.get().saveConfigFile();
            PlatformHolder.get().logMessage("§a已切换到模型: " + currentModel.getName());
        } else {
            PlatformHolder.get().logMessage("§c模型 " + modelName + " 不存在。");
        }
    }

    public Map<String, Model> getModels() {
        return models;
    }

    public void reloadModel() {
        String modelName = PlatformHolder.get().getConfigString("Model", "Default");
        switchModel(modelName.toLowerCase());
        PlatformHolder.get().logMessage("§a模型已重新加载，当前模型: " + currentModel.getName());
    }

    public String getModelMaterialName(String modelName) {
        return PlatformHolder.get().getConfigString("models." + modelName.toLowerCase() + ".material", "PAPER");
    }

    public static String getModelMaterial(String modelName) {
        return PlatformHolder.get().getConfigString("models." + modelName.toLowerCase() + ".material", "PAPER");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

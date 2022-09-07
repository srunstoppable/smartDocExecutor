import com.power.common.util.DateTimeUtil;
import com.power.doc.builder.ApiDocBuilder;
import com.power.doc.builder.DocBuilderTemplate;
import com.power.doc.builder.ProjectDocConfigBuilder;
import com.power.doc.model.ApiConfig;
import com.power.doc.model.ApiDoc;
import com.power.doc.template.IDocBuildTemplate;
import com.power.doc.template.SpringBootDocBuildTemplate;
import com.thoughtworks.qdox.JavaProjectBuilder;

import java.util.List;

/**
 * @Description
 * @Author Shen Rui
 * @Date2022/9/7 16:45
 **/
public class ApiBuilderRevert extends ApiDocBuilder {

    public static void buildApiDoc(ApiConfig config) {
        JavaProjectBuilder javaProjectBuilder = new JavaProjectBuilder();
        buildApiDoc(config, javaProjectBuilder);
    }

    public static void buildApiDoc(ApiConfig config, JavaProjectBuilder javaProjectBuilder) {
        DocBuilderTemplate builderTemplate = new DocBuilderTemplate();
        builderTemplate.checkAndInit(config);
        config.setAdoc(false);
        config.setParamsDataToTree(false);
        ProjectDocConfigBuilder configBuilder = new ProjectDocConfigBuilder(config, javaProjectBuilder);
        IDocBuildTemplate docBuildTemplate = new SpringBootDocBuildTemplate();
        List<ApiDoc> apiDocList = docBuildTemplate.getApiData(configBuilder);
        if (config.isAllInOne()) {
            String version = config.isCoverOld() ? "" : "-V" + DateTimeUtil.long2Str(System.currentTimeMillis(), "yyyyMMddHHmm");
            builderTemplate.buildAllInOne(apiDocList, config, javaProjectBuilder, "AllInOne.btl", "AllInOne" + version + ".md");
        } else {
            builderTemplate.buildApiDoc(apiDocList, config, "ApiDoc.btl", "Api.md");
        }

    }

}

package com.ridup.build;

import com.google.common.base.Strings;
import com.google.gson.JsonObject;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiJavaFileImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.ridup.constant.HttpMethodConstant;
import com.ridup.constant.JavaConstant;
import com.ridup.constant.SpringMVCConstant;
import com.ridup.constant.SwaggerConstants;
import com.ridup.dto.YapiApiDTO;
import com.ridup.dto.YapiHeaderDTO;
import com.ridup.dto.YapiPathVariableDTO;
import com.ridup.dto.YapiQueryDTO;
import com.ridup.upload.YApiSync;
import com.ridup.util.DesUtil;
import com.ridup.util.FileToZipUtil;
import com.ridup.util.FileUnZipUtil;
import com.ridup.util.PsiAnnotationSearchUtil;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * @description: ??????yapi ?????????
 * @author: ridup
 * @date: 2018/10/27
 */
public class BuildJsonForYapi {
    private static NotificationGroup notificationGroup;

    static {
        notificationGroup = new NotificationGroup("BuildJson.NotificationGroup", NotificationDisplayType.BALLOON, true);
    }

    static Set<String> filePaths = new CopyOnWriteArraySet<>();


    /**
     * ???????????? ????????????
     *
     * @param e
     * @return
     */
    public ArrayList<YapiApiDTO> actionPerformedList(AnActionEvent e, String attachUpload, String returnClass) {
        Editor editor = e.getDataContext().getData(CommonDataKeys.EDITOR);
        PsiFile psiFile = e.getDataContext().getData(CommonDataKeys.PSI_FILE);
        String selectedText = e.getRequiredData(CommonDataKeys.EDITOR).getSelectionModel().getSelectedText();
        Project project = editor.getProject();
        PsiElement referenceAt = psiFile.findElementAt(editor.getCaretModel().getOffset());
        PsiClass selectedClass = (PsiClass) PsiTreeUtil.getContextOfType(referenceAt, new Class[]{PsiClass.class});
        String classMenu = null;
        if (Objects.nonNull(selectedClass.getContext())) {
            classMenu = DesUtil.getMenu(selectedClass.getContext().getText().replace(selectedClass.getText(), ""));
        }
        if (Objects.nonNull(selectedClass.getDocComment())) {
            classMenu = DesUtil.getMenu(selectedClass.getText());
        }
        if (StringUtils.isEmpty(classMenu)) {
            classMenu = DesUtil.getMenuFromComment(selectedClass.getText());
            if(StringUtils.isBlank(classMenu)){
                classMenu = DesUtil.camelToLine(selectedClass.getName(),null);
            }
        }
        ArrayList<YapiApiDTO> yapiApiDTOS = new ArrayList<>();
        if (Strings.isNullOrEmpty(selectedText) || selectedText.equals(selectedClass.getName())) {
            PsiMethod[] psiMethods = selectedClass.getMethods();
            for (PsiMethod psiMethodTarget : psiMethods) {
                //??????????????????
                if (!psiMethodTarget.getModifierList().hasModifierProperty("private") && Objects.nonNull(psiMethodTarget.getReturnType())) {
                    YapiApiDTO yapiApiDTO = actionPerformed(selectedClass, psiMethodTarget, project, psiFile, attachUpload, returnClass);
                    if (Objects.nonNull(yapiApiDTO)) {
                        if (Objects.isNull(yapiApiDTO.getMenu())) {
                            yapiApiDTO.setMenu(classMenu);
                        }
                        yapiApiDTOS.add(yapiApiDTO);
                    }
                }
            }
        } else {
            PsiMethod[] psiMethods = selectedClass.getAllMethods();
            //????????????Method
            PsiMethod psiMethodTarget = null;
            for (PsiMethod psiMethod : psiMethods) {
                if (psiMethod.getName().equals(selectedText)) {
                    psiMethodTarget = psiMethod;
                    break;
                }
            }
            if (Objects.nonNull(psiMethodTarget)) {
                YapiApiDTO yapiApiDTO = actionPerformed(selectedClass, psiMethodTarget, project, psiFile, attachUpload, returnClass);
                if (Objects.nonNull(yapiApiDTO)) {
                    if (Objects.isNull(yapiApiDTO.getMenu())) {
                        yapiApiDTO.setMenu(classMenu);
                    }
                    yapiApiDTOS.add(yapiApiDTO);
                }
            } else {
                Notification error = notificationGroup.createNotification("can not find method:" + selectedText, NotificationType.ERROR);
                Notifications.Bus.notify(error, project);
                return null;
            }
        }
        return yapiApiDTOS;
    }


    public static YapiApiDTO actionPerformed(PsiClass selectedClass, PsiMethod psiMethodTarget, Project project, PsiFile psiFile, String attachUpload, String returnClass) {
        YapiApiDTO yapiApiDTO = new YapiApiDTO();
        // ????????????
        StringBuilder path = new StringBuilder();

        // ??????????????????RequestMapping ??????value
        PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(selectedClass, SpringMVCConstant.RequestMapping);
        if (psiAnnotation != null) {
            PsiNameValuePair[] psiNameValuePairs = psiAnnotation.getParameterList().getAttributes();
            if (psiNameValuePairs.length > 0) {
                if (psiNameValuePairs[0].getLiteralValue() != null) {
                    DesUtil.addPath(path, psiNameValuePairs[0].getLiteralValue());
                } else {
                    PsiAnnotationMemberValue psiAnnotationMemberValue = psiAnnotation.findAttributeValue("value");
                    if (psiAnnotationMemberValue != null) {
                        // ?????????????????????????????????
                        PsiReference baseUrlPsiRef = psiAnnotationMemberValue.getReference();
                        PsiElement firstChild = psiAnnotationMemberValue.getFirstChild();
                        PsiElement lastChild = psiAnnotationMemberValue.getLastChild();

                        if(null!=baseUrlPsiRef){
                            String[] results = psiAnnotationMemberValue.getReference().resolve().getText().split("=");
                            DesUtil.addPath(path, results[results.length - 1].split(";")[0].replace("\"", "").trim());
                        }else if(null!=firstChild&&null!=lastChild){
                            String[] results= firstChild.getReference().resolve().getText().split("=");
                            String baseUrl = results[results.length - 1].split(";")[0].replace("\"", "").trim();
                            String suffixUrl = lastChild.getText().replace("\"", "").trim();
                            DesUtil.addPath(path, baseUrl);
                            DesUtil.addPath(path, suffixUrl);
                            // ?????????????????????????????????????????????????????????????????????????????????
                            yapiApiDTO.setBasePath(baseUrl);
                        }else{
                            Notification error = notificationGroup.createNotification("can not find RequestMapping annotation on class", NotificationType.ERROR);
                            Notifications.Bus.notify(error, project);
                            return null;
                        }
                    }else {
                        Notification error = notificationGroup.createNotification("can not find RequestMapping annotation on class", NotificationType.ERROR);
                        Notifications.Bus.notify(error, project);
                        return null;
                    }
                }
            }
        }
        //??????swagger??????
        String operation = PsiAnnotationSearchUtil.getPsiParameterAnnotationValue(psiMethodTarget, SwaggerConstants.API_OPERATION);
        if (StringUtils.isNotEmpty(operation)) {
            Notification info = notificationGroup.createNotification("apiOperation:" + operation, NotificationType.INFORMATION);
            Notifications.Bus.notify(info, project);
            yapiApiDTO.setTitle(operation);
        }
        yapiApiDTO.setPath(path.toString());

        PsiAnnotation psiAnnotationMethod = PsiAnnotationSearchUtil.findAnnotation(psiMethodTarget, SpringMVCConstant.RequestMapping);
        if (psiAnnotationMethod != null) {
            PsiNameValuePair[] psiNameValuePairs = psiAnnotationMethod.getParameterList().getAttributes();
            if (psiNameValuePairs != null && psiNameValuePairs.length > 0) {
                for (PsiNameValuePair psiNameValuePair : psiNameValuePairs) {
                    //????????????????????????
                    if (Objects.isNull(psiNameValuePair.getName()) || "value".equals(psiNameValuePair.getName())) {
                        PsiReference psiReference = psiNameValuePair.getValue().getReference();
                        if (psiReference == null) {
                            DesUtil.addPath(path, psiNameValuePair.getLiteralValue());
                        } else {
                            String[] results = psiReference.resolve().getText().split("=");
                            DesUtil.addPath(path, results[results.length - 1].split(";")[0].replace("\"", "").trim());
                            yapiApiDTO.setTitle(DesUtil.getUrlReFerenceRDesc(psiReference.resolve().getText()));
                            yapiApiDTO.setMenu(DesUtil.getMenu(psiReference.resolve().getText()));
                            yapiApiDTO.setStatus(DesUtil.getStatus(psiReference.resolve().getText()));
                            yapiApiDTO.setDesc("<pre><code>  " + psiReference.resolve().getText() + " </code></pre> <hr>");
                        }
                        yapiApiDTO.setPath(path.toString());
                    } else if ("method".equals(psiNameValuePair.getName()) && psiNameValuePair.getValue().toString().toUpperCase().contains(HttpMethodConstant.GET)) {
                        // ???????????????Get ??????
                        yapiApiDTO.setMethod(HttpMethodConstant.GET);
                    } else if ("method".equals(psiNameValuePair.getName()) && psiNameValuePair.getValue().toString().toUpperCase().contains(HttpMethodConstant.POST)) {
                        // ???????????????Post ??????
                        yapiApiDTO.setMethod(HttpMethodConstant.POST);
                    } else if ("method".equals(psiNameValuePair.getName()) && psiNameValuePair.getValue().toString().toUpperCase().contains(HttpMethodConstant.PUT)) {
                        // ??????????????? PUT ??????
                        yapiApiDTO.setMethod(HttpMethodConstant.PUT);
                    } else if ("method".equals(psiNameValuePair.getName()) && psiNameValuePair.getValue().toString().toUpperCase().contains(HttpMethodConstant.DELETE)) {
                        // ??????????????? DELETE ??????
                        yapiApiDTO.setMethod(HttpMethodConstant.DELETE);
                    } else if ("method".equals(psiNameValuePair.getName()) && psiNameValuePair.getValue().toString().toUpperCase().contains(HttpMethodConstant.PATCH)) {
                        // ??????????????? PATCH ??????
                        yapiApiDTO.setMethod(HttpMethodConstant.PATCH);
                    }
                }
            }
        } else {
            PsiAnnotation psiAnnotationMethodSemple = PsiAnnotationSearchUtil.findAnnotation(psiMethodTarget, SpringMVCConstant.GetMapping);
            if (psiAnnotationMethodSemple != null) {
                yapiApiDTO.setMethod(HttpMethodConstant.GET);
            } else {
                psiAnnotationMethodSemple = PsiAnnotationSearchUtil.findAnnotation(psiMethodTarget, SpringMVCConstant.PostMapping);
                if (psiAnnotationMethodSemple != null) {
                    yapiApiDTO.setMethod(HttpMethodConstant.POST);
                } else {
                    psiAnnotationMethodSemple = PsiAnnotationSearchUtil.findAnnotation(psiMethodTarget, SpringMVCConstant.PutMapping);
                    if (psiAnnotationMethodSemple != null) {
                        yapiApiDTO.setMethod(HttpMethodConstant.PUT);
                    } else {
                        psiAnnotationMethodSemple = PsiAnnotationSearchUtil.findAnnotation(psiMethodTarget, SpringMVCConstant.DeleteMapping);
                        if (psiAnnotationMethodSemple != null) {
                            yapiApiDTO.setMethod(HttpMethodConstant.DELETE);
                        } else {
                            psiAnnotationMethodSemple = PsiAnnotationSearchUtil.findAnnotation(psiMethodTarget, SpringMVCConstant.PatchMapping);
                            if (psiAnnotationMethodSemple != null) {
                                yapiApiDTO.setMethod(HttpMethodConstant.PATCH);
                            }
                        }
                    }
                }
            }
            if (psiAnnotationMethodSemple != null) {
                PsiNameValuePair[] psiNameValuePairs = psiAnnotationMethodSemple.getParameterList().getAttributes();
                if (psiNameValuePairs != null && psiNameValuePairs.length > 0) {
                    for (PsiNameValuePair psiNameValuePair : psiNameValuePairs) {
                        //????????????????????????
                        if (Objects.isNull(psiNameValuePair.getName()) || psiNameValuePair.getName().equals("value")) {
                            PsiReference psiReference = psiNameValuePair.getValue().getReference();
                            if (psiReference == null) {
                                DesUtil.addPath(path, psiNameValuePair.getLiteralValue());
                            } else {
                                String[] results = psiReference.resolve().getText().split("=");
                                DesUtil.addPath(path, results[results.length - 1].split(";")[0].replace("\"", "").trim());
                                yapiApiDTO.setTitle(DesUtil.getUrlReFerenceRDesc(psiReference.resolve().getText()));
                                yapiApiDTO.setMenu(DesUtil.getMenu(psiReference.resolve().getText()));
                                yapiApiDTO.setStatus(DesUtil.getStatus(psiReference.resolve().getText()));
                                if (!Strings.isNullOrEmpty(psiReference.resolve().getText())) {
                                    String refernceDesc = psiReference.resolve().getText().replace("<", "&lt;").replace(">", "&gt;");
                                    yapiApiDTO.setDesc("<pre><code>  " + refernceDesc + " </code></pre> <hr>");
                                }
                            }
                            yapiApiDTO.setPath(path.toString().trim());
                        }
                    }
                }
            }
        }
        String classDesc = psiMethodTarget.getText().replace(Objects.nonNull(psiMethodTarget.getBody()) ? psiMethodTarget.getBody().getText() : "", "");
        if (!Strings.isNullOrEmpty(classDesc)) {
            classDesc = classDesc.replace("<", "&lt;").replace(">", "&gt;");
        }
        yapiApiDTO.setDesc(Objects.nonNull(yapiApiDTO.getDesc()) ? yapiApiDTO.getDesc() : " <pre><code>  " + classDesc + "</code></pre>");
        try {
            // ??????????????????????????????
            filePaths.clear();
            // ??????????????????
            yapiApiDTO.setResponse(getResponse(project, psiMethodTarget.getReturnType(), returnClass));
            Set<String> codeSet = new HashSet<>();
            Long time = System.currentTimeMillis();
            String responseFileName = "/response_" + time + ".zip";
            String requestFileName = "/request_" + time + ".zip";
            String codeFileName = "/code_" + time + ".zip";
            if (!Strings.isNullOrEmpty(attachUpload)) {
                // ????????????????????????
                if (filePaths.size() > 0) {
                    changeFilePath(project);
                    FileToZipUtil.toZip(filePaths, project.getBasePath() + responseFileName, true);
                    filePaths.clear();
                    codeSet.add(project.getBasePath() + responseFileName);
                }
                // ????????????
                // ??????????????????
            } else {
                filePaths.clear();
            }
            getRequest(project, yapiApiDTO, psiMethodTarget);
            if (!Strings.isNullOrEmpty(attachUpload)) {
                if (filePaths.size() > 0) {
                    changeFilePath(project);
                    FileToZipUtil.toZip(filePaths, project.getBasePath() + requestFileName, true);
                    filePaths.clear();
                    codeSet.add(project.getBasePath() + requestFileName);
                }
                // ????????????????????????
                if (codeSet.size() > 0) {
                    FileToZipUtil.toZip(codeSet, project.getBasePath() + codeFileName, true);
                    if (!Strings.isNullOrEmpty(attachUpload)) {
                        String fileUrl = new YApiSync().uploadFile(attachUpload, project.getBasePath() + codeFileName);
                        if (!Strings.isNullOrEmpty(fileUrl)) {
                            yapiApiDTO.setDesc("java???:<a href='" + fileUrl + "'>????????????</a><br/>" + yapiApiDTO.getDesc());
                        }
                    }
                }
            } else {
                filePaths.clear();
            }
            //??????????????????
            if (!Strings.isNullOrEmpty(attachUpload)) {
                File file = new File(project.getBasePath() + codeFileName);
                if (file.exists() && file.isFile()) {
                    file.delete();
                    file = new File(project.getBasePath() + responseFileName);
                    file.delete();
                    file = new File(project.getBasePath() + requestFileName);
                    file.delete();
                }
                // ?????? ??????
            }

            // ????????????
            if (Strings.isNullOrEmpty(yapiApiDTO.getTitle())) {
                yapiApiDTO.setTitle(DesUtil.getDescription(psiMethodTarget));
                if (Objects.nonNull(psiMethodTarget.getDocComment())) {
                    // ????????????
                    String menu = DesUtil.getMenu(psiMethodTarget.getDocComment().getText());
                    if (!Strings.isNullOrEmpty(menu)) {
                        yapiApiDTO.setMenu(menu);
                    }
                    // ????????????
                    String status = DesUtil.getStatus(psiMethodTarget.getDocComment().getText());
                    if (!Strings.isNullOrEmpty(status)) {
                        yapiApiDTO.setStatus(status);
                    }
                    // ?????????????????????
                    String pathCustom=DesUtil.getPath(psiMethodTarget.getDocComment().getText());
                    if(!Strings.isNullOrEmpty(pathCustom)){
                        yapiApiDTO.setPath(pathCustom);
                    }
                }
            }
            return yapiApiDTO;
        } catch (Exception ex) {
            Notification error = notificationGroup.createNotification(Objects.nonNull(ex.getMessage()) ? ex.getMessage() : "build response/request data error", NotificationType.ERROR);
            Notifications.Bus.notify(error, project);
        }
        return null;
    }

    /**
     * @description: ??????????????????
     * @param: [project, yapiApiDTO, psiMethodTarget]
     * @return: void
     * @author: ridup
     * @date: 2019/2/19
     */
    public static void getRequest(Project project, YapiApiDTO yapiApiDTO, PsiMethod psiMethodTarget) throws JSONException {
        PsiParameter[] psiParameters = psiMethodTarget.getParameterList().getParameters();
        if (psiParameters.length > 0) {
            ArrayList yapiParamList = new ArrayList<YapiQueryDTO>();
            List<YapiHeaderDTO> yapiHeaderDTOList = new ArrayList<>();
            List<YapiPathVariableDTO> yapiPathVariableDTOList = new ArrayList<>();
            for (PsiParameter psiParameter : psiParameters) {
                if (JavaConstant.HttpServletRequest.equals(psiParameter.getType().getCanonicalText()) || JavaConstant.HttpServletResponse.equals(psiParameter.getType().getCanonicalText())) {
                    continue;
                }
                PsiAnnotation psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParameter, SpringMVCConstant.RequestBody);
                if (psiAnnotation != null) {
                    yapiApiDTO.setRequestBody(getResponse(project, psiParameter.getType(), null));
                } else {
                    psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParameter, SpringMVCConstant.RequestParam);
                    YapiHeaderDTO yapiHeaderDTO = null;
                    YapiPathVariableDTO yapiPathVariableDTO = null;
                    if (psiAnnotation == null) {
                        psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParameter, SpringMVCConstant.RequestAttribute);
                        if (psiAnnotation == null) {
                            psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParameter, SpringMVCConstant.RequestHeader);
                            if (psiAnnotation == null) {
                                psiAnnotation = PsiAnnotationSearchUtil.findAnnotation(psiParameter, SpringMVCConstant.PathVariable);
                                yapiPathVariableDTO = new YapiPathVariableDTO();
                            } else {
                                yapiHeaderDTO = new YapiHeaderDTO();
                            }
                        }
                    }
                    if (psiAnnotation != null) {
                        PsiNameValuePair[] psiNameValuePairs = psiAnnotation.getParameterList().getAttributes();
                        YapiQueryDTO yapiQueryDTO = new YapiQueryDTO();

                        if (psiNameValuePairs.length > 0) {
                            for (PsiNameValuePair psiNameValuePair : psiNameValuePairs) {
                                if ("name".equals(psiNameValuePair.getName()) || "value".equals(psiNameValuePair.getName())) {
                                    if (yapiHeaderDTO != null) {
                                        yapiHeaderDTO.setName(psiNameValuePair.getValue().getText().replace("\"", ""));
                                    } else if (yapiPathVariableDTO != null) {
                                        yapiPathVariableDTO.setName(psiNameValuePair.getValue().getText().replace("\"", ""));
                                    } else {
                                        yapiQueryDTO.setName(psiNameValuePair.getValue().getText().replace("\"", ""));
                                    }
                                } else if ("required".equals(psiNameValuePair.getName())) {
                                    yapiQueryDTO.setRequired(psiNameValuePair.getValue().getText().replace("\"", "").replace("false", "0").replace("true", "1"));
                                } else if ("defaultValue".equals(psiNameValuePair.getName())) {
                                    if (yapiHeaderDTO != null) {
                                        yapiHeaderDTO.setExample(psiNameValuePair.getValue().getText().replace("\"", ""));
                                    } else {
                                        yapiQueryDTO.setExample(psiNameValuePair.getValue().getText().replace("\"", ""));
                                    }
                                } else {
                                    if (yapiHeaderDTO != null) {
                                        yapiHeaderDTO.setName(psiNameValuePair.getLiteralValue());
                                        // ???????????????????????? ?????? ?????? ??????
                                        yapiHeaderDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                                    }
                                    if (yapiPathVariableDTO != null) {
                                        yapiPathVariableDTO.setName(psiNameValuePair.getLiteralValue());
                                        // ???????????????????????? ?????? ?????? ??????
                                        yapiPathVariableDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                                    } else {
                                        yapiQueryDTO.setName(psiNameValuePair.getLiteralValue());
                                        // ???????????????????????? ?????? ?????? ??????
                                        yapiQueryDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                                    }
                                    if (NormalTypes.normalTypes.containsKey(psiParameter.getType().getPresentableText())) {
                                        if (yapiHeaderDTO != null) {
                                            yapiHeaderDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                                        } else if (yapiPathVariableDTO != null) {
                                            yapiPathVariableDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                                        } else {
                                            yapiQueryDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                                        }
                                    } else {
                                        yapiApiDTO.setRequestBody(getResponse(project, psiParameter.getType(), null));
                                    }

                                }
                            }
                        } else {
                            if (yapiHeaderDTO != null) {
                                yapiHeaderDTO.setName(psiParameter.getName());
                                // ???????????????????????? ?????? ?????? ??????
                                yapiHeaderDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                            } else if (yapiPathVariableDTO != null) {
                                yapiPathVariableDTO.setName(psiParameter.getName());
                                // ???????????????????????? ?????? ?????? ??????
                                yapiPathVariableDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                            } else {
                                yapiQueryDTO.setName(psiParameter.getName());
                                // ???????????????????????? ?????? ?????? ??????
                                yapiQueryDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                            }
                            if (NormalTypes.normalTypes.containsKey(psiParameter.getType().getPresentableText())) {
                                if (yapiHeaderDTO != null) {
                                    yapiHeaderDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                                } else if (yapiPathVariableDTO != null) {
                                    yapiPathVariableDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                                } else {
                                    yapiQueryDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                                }
                            } else {
                                yapiApiDTO.setRequestBody(getResponse(project, psiParameter.getType(), null));
                            }
                        }
                        if (yapiHeaderDTO != null) {
                            if (Strings.isNullOrEmpty(yapiHeaderDTO.getDesc())) {
                                // ???????????????????????? ??????  ?????? ??????
                                yapiHeaderDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                            }
                            if (Strings.isNullOrEmpty(yapiHeaderDTO.getExample()) && NormalTypes.normalTypes.containsKey(psiParameter.getType().getPresentableText())) {
                                yapiHeaderDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                            }
                            if (Strings.isNullOrEmpty(yapiHeaderDTO.getName())) {
                                yapiHeaderDTO.setName(psiParameter.getName());
                            }
                            yapiHeaderDTOList.add(yapiHeaderDTO);
                        } else if (yapiPathVariableDTO != null) {
                            if (Strings.isNullOrEmpty(yapiPathVariableDTO.getDesc())) {
                                // ???????????????????????? ??????  ?????? ??????
                                yapiPathVariableDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                            }
                            if (Strings.isNullOrEmpty(yapiPathVariableDTO.getExample()) && NormalTypes.normalTypes.containsKey(psiParameter.getType().getPresentableText())) {
                                yapiPathVariableDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                            }
                            if (Strings.isNullOrEmpty(yapiPathVariableDTO.getName())) {
                                yapiPathVariableDTO.setName(psiParameter.getName());
                            }
                            String desc = PsiAnnotationSearchUtil.getPsiParameterAnnotationValue(psiParameter, SwaggerConstants.API_PARAM);
                            if (StringUtils.isNotEmpty(desc)) {
                                yapiPathVariableDTO.setDesc(desc);
                            }
                            yapiPathVariableDTOList.add(yapiPathVariableDTO);
                        } else {
                            if (Strings.isNullOrEmpty(yapiQueryDTO.getDesc())) {
                                // ???????????????????????? ?????? ?????? ??????
                                yapiQueryDTO.setDesc(DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")");
                            }
                            if (Strings.isNullOrEmpty(yapiQueryDTO.getExample()) && NormalTypes.normalTypes.containsKey(psiParameter.getType().getPresentableText())) {
                                yapiQueryDTO.setExample(NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
                            }
                            if (Strings.isNullOrEmpty(yapiQueryDTO.getName())) {
                                yapiQueryDTO.setName(psiParameter.getName());
                            }
                            String desc = PsiAnnotationSearchUtil.getPsiParameterAnnotationValue(psiParameter, SwaggerConstants.API_PARAM);
                            if (StringUtils.isNotEmpty(desc)) {
                                yapiQueryDTO.setDesc(desc);
                            }
                            yapiParamList.add(yapiQueryDTO);
                        }
                    } else {
                        if (HttpMethodConstant.GET.equals(yapiApiDTO.getMethod())) {
                            List<Map<String, String>> requestList = getRequestForm(project, psiParameter, psiMethodTarget);
                            for (Map<String, String> map : requestList) {
                                yapiParamList.add(new YapiQueryDTO(map.get("desc"), map.get("example"), map.get("name")));
                            }
                        } else if (HttpMethodConstant.POST.equals(yapiApiDTO.getMethod())) {
                            // ????????????????????????
                            yapiApiDTO.setReq_body_type("form");
                            if (yapiApiDTO.getReq_body_form() != null) {
                                yapiApiDTO.getReq_body_form().addAll(getRequestForm(project, psiParameter, psiMethodTarget));
                            } else {
                                yapiApiDTO.setReq_body_form(getRequestForm(project, psiParameter, psiMethodTarget));
                            }
                        }

                    }
                }
            }
            yapiApiDTO.setParams(yapiParamList);
            yapiApiDTO.setHeader(yapiHeaderDTOList);
            yapiApiDTO.setReq_params(yapiPathVariableDTOList);
        }
    }

    /**
     * @description: ??????????????????????????????
     * @param: [requestClass]
     * @return: java.util.List<java.util.Map < java.lang.String, java.lang.String>>
     * @author: ridup
     * @date: 2019/5/17
     */
    public static List<Map<String, String>> getRequestForm(Project project, PsiParameter psiParameter, PsiMethod psiMethodTarget) {
        List<Map<String, String>> requestForm = new ArrayList<>();
        if (NormalTypes.normalTypes.containsKey(psiParameter.getType().getPresentableText())) {
            Map<String, String> map = new HashMap<>();
            map.put("name", psiParameter.getName());
            map.put("type", "text");
            String remark = DesUtil.getParamDesc(psiMethodTarget, psiParameter.getName()) + "(" + psiParameter.getType().getPresentableText() + ")";
            map.put("desc", remark);
            map.put("example", NormalTypes.normalTypes.get(psiParameter.getType().getPresentableText()).toString());
            requestForm.add(map);
        } else {
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(psiParameter.getType().getCanonicalText(), GlobalSearchScope.allScope(project));
            for (PsiField field : psiClass.getAllFields()) {
                if (field.getModifierList().hasModifierProperty("final")) {
                    continue;
                }
                Map<String, String> map = new HashMap<>();
                map.put("name", field.getName());
                map.put("type", "text");
                String remark = DesUtil.getFiledDesc(field.getDocComment());
                remark = DesUtil.getLinkRemark(remark, project, field);
                map.put("desc", remark);
                if (Objects.nonNull(field.getType().getPresentableText())) {
                    Object obj = NormalTypes.normalTypes.get(field.getType().getPresentableText());
                    if (Objects.nonNull(obj)) {
                        map.put("example", NormalTypes.normalTypes.get(field.getType().getPresentableText()).toString());
                    }
                }
                getFilePath(project, filePaths, DesUtil.getFieldLinks(project, field));
                requestForm.add(map);
            }
        }
        return requestForm;
    }

    /**
     * @description: ??????????????????
     * @param: [project, psiType]
     * @return: java.lang.String
     * @author: ridup
     * @date: 2019/2/19
     */
    public static String getResponse(Project project, PsiType psiType, String returnClass) throws JSONException {
        String response = null;
        /** ??????????????????????????????????????????????????????????????? */
        if (!Strings.isNullOrEmpty(returnClass) && !psiType.getCanonicalText().split("<")[0].equals(returnClass)) {
            PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(returnClass, GlobalSearchScope.allScope(project));
            KV result = new KV();
            List<String> requiredList = new ArrayList<>();
            if (Objects.nonNull(psiClass)) {
                KV kvObject = getFields(psiClass, project, null, null, requiredList, new HashSet<>());
                for (PsiField field : psiClass.getAllFields()) {
                    if (NormalTypes.genericList.contains(field.getType().getPresentableText())) {
                        KV child = getPojoJson(project, psiType);
                        kvObject.set(field.getName(), child);
                    }
                }
                result.set("type", "object");
                result.set("title", psiClass.getName());
                result.set("required", requiredList);
                result.set("description", psiClass.getQualifiedName());
                result.set("properties", kvObject);
            } else {
                throw new RuntimeException("can not find class:" + returnClass);
            }
            response = result.toPrettyJson();
        } else {
            KV kv = getPojoJson(project, psiType);
            response = Objects.isNull(kv) ? "" : kv.toPrettyJson();
        }
        return response;
    }


    public static KV getPojoJson(Project project, PsiType psiType) throws JSONException {
        if (psiType instanceof PsiPrimitiveType) {
            //?????????????????????
            KV kvClass = KV.create();
            kvClass.set(psiType.getCanonicalText(), NormalTypes.normalTypes.get(psiType.getPresentableText()));
        } else if (NormalTypes.isNormalType(psiType.getPresentableText())) {
            //?????????????????????
            KV kvClass = KV.create();
            kvClass.set(psiType.getCanonicalText(), NormalTypes.normalTypes.get(psiType.getPresentableText()));
        } else if (psiType.getPresentableText().startsWith("List<")) {
            String[] types = psiType.getCanonicalText().split("<");
            KV listKv = new KV();
            if (types.length > 1) {
                String childPackage = types[1].split(">")[0];
                if (NormalTypes.noramlTypesPackages.keySet().contains(childPackage)) {
                    String[] childTypes = childPackage.split("\\.");
                    listKv.set("type", childTypes[childTypes.length - 1]);
                } else if (NormalTypes.collectTypesPackages.containsKey(childPackage)) {
                    String[] childTypes = childPackage.split("\\.");
                    listKv.set("type", childTypes[childTypes.length - 1]);
                } else {
                    PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(childPackage, GlobalSearchScope.allScope(project));
                    List<String> requiredList = new ArrayList<>();
                    KV kvObject = getFields(psiClassChild, project, null, null, requiredList, new HashSet<>());
                    listKv.set("type", "object");
                    addFilePaths(filePaths, psiClassChild);
                    if (Objects.nonNull(psiClassChild.getSuperClass()) && !psiClassChild.getSuperClass().getName().toString().equals("Object")) {
                        addFilePaths(filePaths, psiClassChild.getSuperClass());
                    }
                    listKv.set("properties", kvObject);
                    listKv.set("required", requiredList);
                }
            }
            KV result = new KV();
            result.set("type", "array");
            result.set("title", psiType.getPresentableText());
            result.set("description", psiType.getPresentableText());
            result.set("items", listKv);
            return result;
        } else if (psiType.getPresentableText().startsWith("Set<")) {
            String[] types = psiType.getCanonicalText().split("<");
            KV listKv = new KV();
            if (types.length > 1) {
                String childPackage = types[1].split(">")[0];
                if (NormalTypes.noramlTypesPackages.keySet().contains(childPackage)) {
                    String[] childTypes = childPackage.split("\\.");
                    listKv.set("type", childTypes[childTypes.length - 1]);
                } else if (NormalTypes.collectTypesPackages.containsKey(childPackage)) {
                    String[] childTypes = childPackage.split("\\.");
                    listKv.set("type", childTypes[childTypes.length - 1]);
                } else {
                    PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(childPackage, GlobalSearchScope.allScope(project));
                    List<String> requiredList = new ArrayList<>();
                    KV kvObject = getFields(psiClassChild, project, null, null, requiredList, new HashSet<>());
                    listKv.set("type", "object");
                    addFilePaths(filePaths, psiClassChild);
                    if (Objects.nonNull(psiClassChild.getSuperClass()) && !psiClassChild.getSuperClass().getName().toString().equals("Object")) {
                        addFilePaths(filePaths, psiClassChild.getSuperClass());
                    }
                    listKv.set("properties", kvObject);
                    listKv.set("required", requiredList);
                }
            }
            KV result = new KV();
            result.set("type", "array");
            result.set("title", psiType.getPresentableText());
            result.set("description", psiType.getPresentableText());
            result.set("items", listKv);
            return result;
        } else if (psiType.getPresentableText().startsWith("Map<") || psiType.getPresentableText().startsWith("HashMap<") || psiType.getPresentableText().startsWith("LinkedHashMap<")) {
            KV kv1 = new KV();
            kv1.set(KV.by("type", "object"));
            kv1.set(KV.by("description", "(????????????map)"));
            if (((PsiClassReferenceType) psiType).getParameters().length > 1) {
                KV keyObj = new KV();
                keyObj.set("type", "object");
                keyObj.set("description", ((PsiClassReferenceType) psiType).getParameters()[1].getPresentableText());
                keyObj.set("properties", getFields(PsiUtil.resolveClassInType(((PsiClassReferenceType) psiType).getParameters()[1]), project, null, 0, new ArrayList<>(), new HashSet<>()));

                KV key = new KV();
                key.set("type", "object");
                key.set("description", ((PsiClassReferenceType) psiType).getParameters()[0].getPresentableText());

                KV keyObjSup = new KV();
                keyObjSup.set("mapKey", key);
                keyObjSup.set("mapValue", keyObj);
                kv1.set("properties", keyObjSup);
            } else {
                kv1.set(KV.by("description", "?????????Map<?,?>"));
            }
            return kv1;
        } else if (NormalTypes.collectTypes.containsKey(psiType.getPresentableText())) {
            //?????????????????????
            KV kvClass = KV.create();
            kvClass.set(psiType.getCanonicalText(), NormalTypes.collectTypes.get(psiType.getPresentableText()));
        } else {
            String[] types = psiType.getCanonicalText().split("<");
            if (types.length > 1) {
                PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(types[0], GlobalSearchScope.allScope(project));
                KV result = new KV();
                List<String> requiredList = new ArrayList<>();
                KV kvObject = getFields(psiClassChild, project, types, 1, requiredList, new HashSet<>());
                result.set("type", "object");
                result.set("title", psiType.getPresentableText());
                result.set("required", requiredList);
                addFilePaths(filePaths, psiClassChild);
                if (Objects.nonNull(psiClassChild.getSuperClass()) && !psiClassChild.getSuperClass().getName().toString().equals("Object")) {
                    addFilePaths(filePaths, psiClassChild.getSuperClass());
                }
                result.set("description", (psiType.getPresentableText() + " :" + psiClassChild.getName()).trim());
                result.set("properties", kvObject);
                return result;
            } else {
                PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(psiType.getCanonicalText(), GlobalSearchScope.allScope(project));
                KV result = new KV();
                List<String> requiredList = new ArrayList<>();
                KV kvObject = getFields(psiClassChild, project, null, null, requiredList, new HashSet<>());
                addFilePaths(filePaths, psiClassChild);
                if (Objects.nonNull(psiClassChild.getSuperClass()) && !psiClassChild.getSuperClass().getName().toString().equals("Object")) {
                    addFilePaths(filePaths, psiClassChild.getSuperClass());
                }
                result.set("type", "object");
                result.set("required", requiredList);
                result.set("title", psiType.getPresentableText());
                result.set("description", (psiType.getPresentableText() + " :" + psiClassChild.getName()).trim());
                result.set("properties", kvObject);
                return result;
            }
        }
        return null;
    }

    /**
     * @description: ??????????????????
     * @param: [psiClass, project, childType, index]
     * @return: com.ridup.build.KV
     * @author: ridup
     * @date: 2019/5/15
     */
    public static KV getFields(PsiClass psiClass, Project project, String[] childType, Integer index, List<String> requiredList, Set<String> pNames) {
        KV kv = KV.create();
        if (psiClass != null) {
            if (Objects.nonNull(psiClass.getSuperClass()) && Objects.nonNull(NormalTypes.collectTypes.get(psiClass.getSuperClass().getName()))) {
                for (PsiField field : psiClass.getFields()) {
                    if (Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.Deprecate))) {
                        continue;
                    }
                    //????????????notnull ??? notEmpty ?????????????????????
                    if (Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotNull))
                        || Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotEmpty))
                        || Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotBlank))) {
                        requiredList.add(field.getName());
                    }
                    Set<String> pNameList = new HashSet<>();
                    pNameList.addAll(pNames);
                    pNameList.add(psiClass.getName());
                    getField(field, project, kv, childType, index, pNameList);
                }
            } else {
                if (NormalTypes.genericList.contains(psiClass.getName()) && childType != null && childType.length > index) {
                    String child = childType[index].split(">")[0];
                    PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
                    getFilePath(project, filePaths, Arrays.asList(psiClassChild));
                    return getFields(psiClassChild, project, childType, index + 1, requiredList, pNames);
                } else {
                    for (PsiField field : psiClass.getAllFields()) {
                        if (Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.Deprecate))) {
                            continue;
                        }
                        //????????????notnull ??? notEmpty ?????????????????????
                        if (Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotNull))
                            || Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotEmpty))
                            || Objects.nonNull(PsiAnnotationSearchUtil.findAnnotation(field, JavaConstant.NotBlank))) {
                            requiredList.add(field.getName());
                        }
                        Set<String> pNameList = new HashSet<>();
                        pNameList.addAll(pNames);
                        pNameList.add(psiClass.getName());
                        getField(field, project, kv, childType, index, pNameList);
                    }
                }
            }
        }
        return kv;
    }

    /**
     * @description: ??????????????????
     * @param: [field, project, kv, childType, index, pName]
     * @return: void
     * @author: ridup
     * @date: 2019/5/15
     */
    public static void getField(PsiField field, Project project, KV kv, String[] childType, Integer index, Set<String> pNames) {
        if (field.getModifierList().hasModifierProperty("final")) {
            return;
        }
        PsiType type = field.getType();
        String name = field.getName();
        String remark = "";
        //swagger??????
        remark = StringUtils.defaultIfEmpty(PsiAnnotationSearchUtil.getPsiParameterAnnotationValue(field, SwaggerConstants.API_MODEL_PROPERTY), "");
        if (field.getDocComment() != null) {
            if(Strings.isNullOrEmpty(remark)) {
                remark = DesUtil.getFiledDesc(field.getDocComment());
            }
            //??????link ??????
            remark = DesUtil.getLinkRemark(remark, project, field);
            getFilePath(project, filePaths, DesUtil.getFieldLinks(project, field));
        }



        // ?????????????????????
        if (type instanceof PsiPrimitiveType) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("type", type.getPresentableText());
            if (!Strings.isNullOrEmpty(remark)) {
                jsonObject.addProperty("description", remark);
            }
            jsonObject.add("mock", NormalTypes.formatMockType(type.getPresentableText()
                , PsiAnnotationSearchUtil.getPsiParameterAnnotationParam(field, SwaggerConstants.API_MODEL_PROPERTY, "example")));
            kv.set(name, jsonObject);
        } else {
            //reference Type
            String fieldTypeName = type.getPresentableText();
            //normal Type
            if (NormalTypes.isNormalType(fieldTypeName)) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("type", fieldTypeName);
                if (!Strings.isNullOrEmpty(remark)) {
                    jsonObject.addProperty("description", remark);
                }
                jsonObject.add("mock", NormalTypes.formatMockType(type.getPresentableText()
                    , PsiAnnotationSearchUtil.getPsiParameterAnnotationParam(field, SwaggerConstants.API_MODEL_PROPERTY, "example")));
                kv.set(name, jsonObject);
            } else if (!(type instanceof PsiArrayType) && ((PsiClassReferenceType) type).resolve().isEnum()) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("type", "enum");
                /*if (Strings.isNullOrEmpty(remark)) {
                    PsiField[] fields = ((PsiClassReferenceType) type).resolve().getAllFields();
                    List<PsiField> fieldList = Arrays.stream(fields).filter(f -> f instanceof PsiEnumConstant).collect(Collectors.toList());
                    StringBuilder remarkBuilder = new StringBuilder();
                    for (PsiField psiField : fieldList) {
                        String comment = DesUtil.getFiledDesc(psiField.getDocComment());
                        comment = Strings.isNullOrEmpty(comment) ? comment : "-" + comment;
                        remarkBuilder.append(psiField.getName()).append(comment);
                        remarkBuilder.append("\n");
                    }
                    remark = remarkBuilder.toString();
                }*/
                PsiField[] fields = ((PsiClassReferenceType) type).resolve().getAllFields();
                String enumRemark = DesUtil.remarkFromEnum(fields, ((PsiClassReferenceType) type).getName());
                jsonObject.addProperty("description", remark+"\n"+enumRemark);
                kv.set(name, jsonObject);
            } else if (NormalTypes.genericList.contains(fieldTypeName)) {
                if (childType != null) {
                    String child = childType[index].split(">")[0];
                    if (child.contains("java.util.List<") || child.contains("java.util.Set<") || child.contains("java.util.HashSet<")) {
                        index = index + 1;
                        PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(childType[index].split(">")[0], GlobalSearchScope.allScope(project));
                        getCollect(kv, psiClassChild.getName(), remark, psiClassChild, project, name, pNames, childType, index + 1);
                    } else if (NormalTypes.isNormalType(child) || NormalTypes.noramlTypesPackages.containsKey(child)) {
                        KV kv1 = new KV();
                        PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
                        kv1.set(KV.by("type", psiClassChild.getName()));
                        kv.set(name, kv1);
                        kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? name : remark)));
                        kv1.set(KV.by("mock", NormalTypes.formatMockType(child
                            , PsiAnnotationSearchUtil.getPsiParameterAnnotationParam(field, SwaggerConstants.API_MODEL_PROPERTY, "example"))));
                    } else {
                        //class type
                        KV kv1 = new KV();
                        kv1.set(KV.by("type", "object"));
                        PsiClass psiClassChild = JavaPsiFacade.getInstance(project).findClass(child, GlobalSearchScope.allScope(project));
                        kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClassChild.getName().trim()) : remark + " ," + psiClassChild.getName().trim())));
                        if (!pNames.contains(psiClassChild.getName())) {
                            List<String> requiredList = new ArrayList<>();
                            kv1.set(KV.by("properties", getFields(psiClassChild, project, childType, index + 1, requiredList, pNames)));
                            kv1.set("required", requiredList);
                            addFilePaths(filePaths, psiClassChild);
                        } else {
                            kv1.set(KV.by("type", psiClassChild.getName()));
                        }
                        kv.set(name, kv1);
                    }
                }
                //    getField()
            } else if (type instanceof PsiArrayType) {
                //array type
                PsiType deepType = type.getDeepComponentType();
                KV kvlist = new KV();
                String deepTypeName = deepType.getPresentableText();
                String cType = "";
                if (deepType instanceof PsiPrimitiveType) {
                    kvlist.set("type", type.getPresentableText());
                    if (!Strings.isNullOrEmpty(remark)) {
                        kvlist.set("description", remark);
                    }
                } else if (NormalTypes.isNormalType(deepTypeName)) {
                    kvlist.set("type", deepTypeName);
                    if (!Strings.isNullOrEmpty(remark)) {
                        kvlist.set("description", remark);
                    }
                } else {
                    kvlist.set(KV.by("type", "object"));
                    PsiClass psiClass = PsiUtil.resolveClassInType(deepType);
                    cType = psiClass.getName();
                    kvlist.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : remark + " ," + psiClass.getName().trim())));
                    if (!pNames.contains(PsiUtil.resolveClassInType(deepType).getName())) {
                        List<String> requiredList = new ArrayList<>();
                        kvlist.set("properties", getFields(psiClass, project, null, null, requiredList, pNames));
                        kvlist.set("required", requiredList);
                        addFilePaths(filePaths, psiClass);
                    } else {
                        kvlist.set(KV.by("type", PsiUtil.resolveClassInType(deepType).getName()));
                    }
                }
                KV kv1 = new KV();
                kv1.set(KV.by("type", "array"));
                kv1.set(KV.by("description", (remark + " :" + cType).trim()));
                kv1.set("items", kvlist);
                kv.set(name, kv1);
            } else if (fieldTypeName.startsWith("List<") || fieldTypeName.startsWith("Set<") || fieldTypeName.startsWith("HashSet<")) {
                //list type
                PsiType iterableType = PsiUtil.extractIterableTypeParameter(type, false);
                PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                if (Objects.nonNull(iterableClass)) {
                    String classTypeName = iterableClass.getName();
                    getCollect(kv, classTypeName, remark, iterableClass, project, name, pNames, childType, index);
                }
            } else if (fieldTypeName.startsWith("HashMap<") || fieldTypeName.startsWith("Map<") || fieldTypeName.startsWith("LinkedHashMap<")) {
                //HashMap or Map
                KV kv1 = new KV();
                kv1.set(KV.by("type", "object"));
                kv1.set(KV.by("description", remark + "(????????????map)"));
                if (((PsiClassReferenceType) type).getParameters().length > 1) {
                    KV keyObj = new KV();
                    keyObj.set("type", "object");
                    keyObj.set("description", ((PsiClassReferenceType) type).getParameters()[1].getPresentableText());
                    keyObj.set("properties", getFields(PsiUtil.resolveClassInType(((PsiClassReferenceType) type).getParameters()[1]), project, childType, index, new ArrayList<>(), pNames));

                    KV key = new KV();
                    key.set("type", "object");
                    key.set("description", ((PsiClassReferenceType) type).getParameters()[0].getPresentableText());

                    KV keyObjSup = new KV();
                    keyObjSup.set("mapKey", key);
                    keyObjSup.set("mapValue", keyObj);
                    kv1.set("properties", keyObjSup);
                } else {
                    kv1.set(KV.by("description", "?????????Map<?,?>"));
                }
                kv.set(name, kv1);
            } else {
                //class type
                KV kv1 = new KV();
                PsiClass psiClass = PsiUtil.resolveClassInType(type);
                kv1.set(KV.by("type", "object"));
                kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : (remark + " ," + psiClass.getName()).trim())));
                if (!pNames.contains(((PsiClassReferenceType) type).getClassName())) {
                    addFilePaths(filePaths, psiClass);
                    List<String> requiredList = new ArrayList<>();
                    kv1.set(KV.by("properties", getFields(PsiUtil.resolveClassInType(type), project, childType, index, requiredList, pNames)));
                    kv1.set("required", requiredList);
                } else {
                    kv1.set(KV.by("type", ((PsiClassReferenceType) type).getClassName()));
                }
                kv.set(name, kv1);
            }
        }
    }


    /**
     * @description: ????????????
     * @param: [kv, classTypeName, remark, psiClass, project, name, pName]
     * @return: void
     * @author: ridup
     * @date: 2019/5/15
     */
    public static void getCollect(KV kv, String classTypeName, String remark, PsiClass psiClass, Project project, String name, Set<String> pNames, String[] childType, Integer index) {
        KV kvlist = new KV();
        if (NormalTypes.isNormalType(classTypeName) || NormalTypes.collectTypes.containsKey(classTypeName)) {
            kvlist.set("type", classTypeName);
            if (!Strings.isNullOrEmpty(remark)) {
                kvlist.set("description", remark);
            }
        } else {
            kvlist.set(KV.by("type", "object"));
            kvlist.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : remark + " ," + psiClass.getName().trim())));
            if (!pNames.contains(psiClass.getName())) {
                List<String> requiredList = new ArrayList<>();
                kvlist.set("properties", getFields(psiClass, project, childType, index, requiredList, pNames));
                kvlist.set("required", requiredList);
                addFilePaths(filePaths, psiClass);
            } else {
                kvlist.set(KV.by("type", psiClass.getName()));
            }
        }
        KV kv1 = new KV();
        kv1.set(KV.by("type", "array"));
        kv1.set(KV.by("description", (Strings.isNullOrEmpty(remark) ? ("" + psiClass.getName().trim()) : remark + " ," + psiClass.getName().trim())));
        kv1.set("items", kvlist);
        kv.set(name, kv1);
    }

    /**
     * @description: ???????????????????????????
     * @param: [filePaths, psiClass]
     * @return: void
     * @author: ridup
     * @date: 2019/5/6
     */
    public static boolean addFilePaths(Set<String> filePaths, PsiClass psiClass) {
        try {
            if(!filePaths.contains(((PsiJavaFileImpl) psiClass.getContext()).getViewProvider().getVirtualFile().getPath())) {
                filePaths.add(((PsiJavaFileImpl) psiClass.getContext()).getViewProvider().getVirtualFile().getPath());
                return true;
            }else {
                return false;
            }
        } catch (Exception e) {
            try {
                if(!filePaths.contains(((ClsFileImpl) psiClass.getContext()).getViewProvider().getVirtualFile().getPath())) {
                    filePaths.add(((ClsFileImpl) psiClass.getContext()).getViewProvider().getVirtualFile().getPath());
                    return true;
                }else{
                    return false;
                }
            } catch (Exception e1) {
            }
        }
        return false;
    }


    /**
     * @description: ??????????????????
     * @param: [project]
     * @return: void
     * @author: ridup
     * @date: 2019/5/6
     */
    public static void changeFilePath(Project project) {
        Set<String> changeFilePaths = filePaths.stream().map(filePath -> {
            if (filePath.contains(".jar")) {
                String[] filePathsubs = filePath.split("\\.jar");
                String jarPath = filePathsubs[0] + "-sources.jar";
                try {
                    //??????????????????
                    FileUnZipUtil.uncompress(new File(jarPath), new File(filePathsubs[0]));
                    filePath = filePathsubs[0] + filePathsubs[1].replace("!", "");
                    return filePath.replace(".class", ".java");
                } catch (IOException e) {
                    Notification error = notificationGroup.createNotification("can not find sources java:" + jarPath, NotificationType.ERROR);
                    Notifications.Bus.notify(error, project);
                }
            }
            return filePath;
        }).collect(Collectors.toSet());
        filePaths.clear();
        filePaths.addAll(changeFilePaths);
    }


    public static void getFilePath(Project project, Set<String> filePaths, List<PsiClass> psiClasses) {
        psiClasses.forEach(psiClass -> {
            if(addFilePaths(filePaths, psiClass)) {
                if (!psiClass.isEnum()) {
                    for (PsiField field : psiClass.getFields()) {
                        // ??????link ??????link
                        getFilePath(project, filePaths, DesUtil.getFieldLinks(project, field));
                        String fieldTypeName = field.getType().getPresentableText();
                        // ??????????????????
                        if (field.getType() instanceof PsiArrayType) {
                            //array type
                            PsiType deepType = field.getType().getDeepComponentType();
                            KV kvlist = new KV();
                            String deepTypeName = deepType.getPresentableText();
                            if (!(deepType instanceof PsiPrimitiveType) && !NormalTypes.isNormalType(deepTypeName)) {
                                psiClass = PsiUtil.resolveClassInType(deepType);
                                getFilePath(project, filePaths, Arrays.asList(psiClass));
                            }
                        } else if (fieldTypeName.startsWith("List<") || fieldTypeName.startsWith("Set<") || fieldTypeName.startsWith("HashSet<")) {
                            //list type
                            PsiType iterableType = PsiUtil.extractIterableTypeParameter(field.getType(), false);
                            PsiClass iterableClass = PsiUtil.resolveClassInClassTypeOnly(iterableType);
                            if (Objects.nonNull(iterableClass)) {
                                String classTypeName = iterableClass.getName();
                                if (!NormalTypes.isNormalType(classTypeName) && !NormalTypes.collectTypes.containsKey(classTypeName)) {
                                    // addFilePaths(filePaths,iterableClass);
                                    getFilePath(project, filePaths, Arrays.asList(iterableClass));
                                }
                            }
                        } else if (fieldTypeName.startsWith("HashMap<") || fieldTypeName.startsWith("Map<") || fieldTypeName.startsWith("LinkedHashMap<")) {
                            //HashMap or Map
                            if (((PsiClassReferenceType) field.getType()).getParameters().length > 1) {
                                PsiClass hashClass = PsiUtil.resolveClassInType(((PsiClassReferenceType) field.getType()).getParameters()[1]);
                                getFilePath(project, filePaths, Arrays.asList(hashClass));
                            }
                        } else if (!(field.getType() instanceof PsiPrimitiveType) && !NormalTypes.isNormalType(fieldTypeName) && !NormalTypes.isNormalType(field.getName())) {
                            //class type
                            psiClass = PsiUtil.resolveClassInType(field.getType());
                            // addFilePaths(filePaths,psiClass);
                            getFilePath(project, filePaths, Arrays.asList(psiClass));
                        }
                    }
                }
            }
        });
    }



}

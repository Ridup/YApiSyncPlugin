package com.ridup.enumeration;

/**
 * 管理台请求路径
 *
 * @author mu_ran@yahoo.com
 * @version V3.0
 * @since 3.0.1 2020/6/15 0:03
 */
public enum EnumRESTFUL {

    /** 统一前缀 */
    BASE_URL_PREFIX("/hsjry/admin", "统一前缀"),

    /** 工作台 */
    MODULE_WORKBENCH_PREFIX("/workbench", "工作台"),

    /** 营运中心 */
    MODULE_OPERATION_PREFIX("/operation", "营运中心"),

    /** 消费金融 */
    MODULE_CONSUMER_FINANCIAL_PREFIX("/consumer-financial", "消费金融"),

    /** 小微金融 */
    MODULE_MICRO_FINANCIAL_PREFIX("/micro-financial", "小微金融"),

    /** 贷后系统 */
    MODULE_POST_LOAN_PREFIX("/post-loan", "贷后系统"),

    /** 审批系统 */
    MODULE_FLOW_PREFIX("/flow", "审批系统"),

    /** 账务系统 */
    MODULE_BIG_ACCT_PREFIX("/big-acct", "账务系统"),

    /** 风控系统 */
    MODULE_RISK_CTRL_PREFIX("/risk-ctrl", "风控系统"),

    /** 报表系统 */
    MODULE_REPORT_PREFIX("/report", "报表系统"),

    /** 管理后台 */
    MODULE_MANAGER_PREFIX("/manager", "管理后台"),

    /** 管理台 */
    MODULE_ADMIN_PREFIX("/admin", "管理台"),
    ;

    /** 状态码 */
    private String code;

    /** 状态描述 */
    private String description;

    private EnumRESTFUL(final String code, final String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据编码查找枚举
     *
     * @param code 编码
     * @return {@link EnumRESTFUL } 实例
     **/
    public static EnumRESTFUL find(String code) {
        for (EnumRESTFUL instance : EnumRESTFUL.values()) {
            if (instance.getCode()
                .equals(code)) {
                return instance;
            }
        }
        return null;
    }

    public String getCode() {
        return this.code;
    }

    public String getDescription() {
        return this.description;
    }
}

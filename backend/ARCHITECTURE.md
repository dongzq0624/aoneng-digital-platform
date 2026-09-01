# åç«¯å¤æ¨¡åæ¶æ

## 1. æ¨¡åèè´£ä¸ä¾èµ

| æ¨¡å | èè´£ | åè®¸ä¾èµ |
| --- | --- | --- |
| `rag-common` | éç¨å¼å¸¸ãç»ä¸éè¯¯ååºãå¸¸éãæ ä¸å¡å·¥å·åè·¨æ¨¡å DTO | JDKãæ ä¸å¡çåºç¡åº |
| `rag-framework` | Spring éç½®ãéç½®å±æ§ãçº¿ç¨æ± ãJWT å·¥å·ä¸è¿æ»¤å¨ãå®å¨é¾ãå¨å±å¼å¸¸å¤ç | `rag-common` ååºç¡è®¾æ½ SDK |
| `rag-biz` | HTTP Controllerãä¸å¡ Serviceãæ°æ®åºè®¿é®ãææ¡£è§£æãå¯¹è±¡å­å¨ãåéæ£ç´¢å RAG ç¼æ | `rag-common`ã`rag-framework` |
| `rag-task` | å®æ¶ä»»å¡ãæ¶æ¯æ¶è´¹åéè¯å¥å£ | `rag-biz`ï¼åªè½è°ç¨å¬å¼ Service æ¥å£ |

ä¾èµæ¹ååºå®ä¸ºï¼

```text
rag-common <- rag-framework <- rag-biz <- rag-task
```

ç¦æ­¢ååä¾èµï¼ç¦æ­¢ `rag-common` å¼ç¨ä¸å¡ç±»ï¼ç¦æ­¢ `rag-framework` å¼ç¨ Controller æå·ä½ä¸å¡å®ç°ï¼ç¦æ­¢ `rag-biz` å¼ç¨ `rag-task`ãè¿æ ·å¯ä»¥é¿åå¾ªç¯ä¾èµï¼å¹¶æ¯ææªæ¥å°ä»»å¡æ¨¡åææç¬ç«é¨ç½²ååã

## 2. å½åç®å½

```text
backend
âââ pom.xml                         # rag-parent èåç¶å·¥ç¨åç»ä¸çæ¬
âââ rag-common
â   âââ src/main/java/com/aoneng/rag/common
â       âââ exception                # ApiErrorResponse åä¸å¡å¼å¸¸
âââ rag-framework
â   âââ src/main/java/com/aoneng/rag/framework
â       âââ advice                   # @RestControllerAdvice
â       âââ config                   # SpringãMinIOãQdrantãæ¨¡ååçº¿ç¨æ± éç½®
â       âââ security                 # JWT è§£æãè®¤è¯è¿æ»¤å¨åå®å¨éé
âââ rag-biz
â   âââ src/main/java/com/aoneng/rag
â       âââ RagApplication.java
â       âââ auth/controller          # ç»å½ãå½åç¨æ·æ¥å£
â       âââ auth/dto                 # LoginDTOãLoginResponseãUserSummary
â       âââ doc/controller           # ç¥è¯åºåææ¡£ HTTP æ¥å£
â       âââ chat/controller          # RAG å¯¹è¯å SSE æ¥å£
â       âââ system/controller       # ç¨æ·ãé¨é¨ãè§è²åèåæ¥å£
â       âââ audit/controller         # å®¡è®¡æ¥è¯¢æ¥å£
â       âââ dashboard/controller    # ä»ªè¡¨çæ¥å£
â       âââ service                  # ç°æä¸å¡å®ç°ï¼æé¢åéæ­¥æå
âââ rag-task
    âââ src/main/java                # é¢çä»»å¡åæ¶æ¯æ¶è´¹å¥å£
```

å½å `service` ä¸ä»æå¼å®¹æ§ç `PlatformRepository` åè¥å¹²æå¡ç±»ï¼ä¿è¯ç°æ APIãæ°æ®åºååç«¯è°ç¨ä¸åãå®ä»¬å±äº `rag-biz`ï¼ä¸ä¼è¢«æ°æ¨¡åå¼ç¨ï¼æ°å¢ä»£ç ä¸å¾ç»§ç»­æ©å¤§è¿ä¸ªé¡¶å±æ··ååã

## 3. ä¸å¡æ¨¡åç®æ åå±

```text
com.aoneng.rag.doc
âââ controller       # ä» HTTPã@Validãè°ç¨ Serviceãè¿å VO/Result
âââ dto              # åç«¯å¥å
âââ vo               # è±æåçåç«¯åºå
âââ po               # æ°æ®åºå®ä½ï¼ä» Mapper/Service åé¨ä½¿ç¨
âââ mapper           # åæ°å CRUDï¼å¤æ SQL æ¾ resources/mybatis/mapper/*.xml
âââ service
â   âââ IDocumentService.java
â   âââ impl/DocumentServiceImpl.java
âââ convert          # MapStruct DTO <-> PO <-> VO
```

`chat`ã`vector`ã`system`ã`audit` å `dashboard` æåæ ·çé¢åè¾¹çç»ç»ãController ä¸æ³¨å¥ MapperãJdbcTemplateãMinioClientãQdrant å®¢æ·ç«¯ææ¨¡åå®¢æ·ç«¯ï¼äºå¡åªæ¾å¨ ServiceImpl ç public æ¹æ³ä¸ãå®æ¶ä»»å¡åªä¾èµ Service æ¥å£ï¼ä¸å¤å¶ä¸å¡é»è¾ã

## 4. éç½®ä¸ç¯å¢

åºç¨éç½®ä½äº `rag-biz/src/main/resources/application.yml`ï¼å¯éè¿ `application-dev.yml`ã`application-prod.yml` è¦çãMinIOãQdrantãPostgreSQLãJWT å DashScope çå¯é¥åªä»ç¯å¢åéè¯»åï¼ç°æåéååé»è®¤è¡ä¸ºä¿æå¼å®¹ãæ¡æ¶éç½®å±æ§éä¸­å¨ `rag-framework`ï¼ç±å¯å¨ç±»ç `@ConfigurationPropertiesScan` æ³¨åã

## 5. åç»­è¿ç§»é¡ºåº

1. å° `PlatformRepository` æ `system`ã`doc`ã`chat`ã`audit`ã`dashboard` æä¸ºé¢å Mapper å Serviceã
2. ä¸ºææ¡£ãå¯¹è¯ååéé¢åè¡¥é½ POãDTOãVO ä»¥å MapStruct Convertï¼å¹¶è¿ç§» Controllerã
3. å°å¤æ SQL ç§»å¥ MyBatis Mapper XMLï¼ä¿çåæ°åæ¥è¯¢åç°æå­æ®µå¼å®¹æ§ã
4. å¨ `rag-task` å¢å å¯éç½®çå®æ¶ä»»å¡/æ¶è´¹å¼å³ãå¹ç­åéè¯ç­ç¥ï¼åªè°ç¨ä¸å¡ Serviceã
5. ä¸ºè®¤è¯ãæéèå´ãææ¡£ç´¢å¼å¤±è´¥ãSSE éè¯¯åå¨å±å¼å¸¸å¢å ååæµè¯ä¸ MockMvc éææµè¯ã

## 6. æå»ºéªè¯

```bash
cd backend
mvn clean test
mvn -DskipTests package
git diff --check
```

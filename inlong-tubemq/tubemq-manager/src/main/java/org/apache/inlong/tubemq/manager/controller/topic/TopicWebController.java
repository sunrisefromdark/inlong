/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.tubemq.manager.controller.topic;

import org.apache.inlong.tubemq.manager.controller.TubeMQResult;
import org.apache.inlong.tubemq.manager.controller.node.request.BatchAddTopicReq;
import org.apache.inlong.tubemq.manager.controller.node.request.CloneTopicReq;
import org.apache.inlong.tubemq.manager.controller.topic.request.DeleteTopicReq;
import org.apache.inlong.tubemq.manager.controller.topic.request.ModifyTopicReq;
import org.apache.inlong.tubemq.manager.controller.topic.request.QueryCanWriteReq;
import org.apache.inlong.tubemq.manager.controller.topic.request.SetAuthControlReq;
import org.apache.inlong.tubemq.manager.controller.topic.request.SetPublishReq;
import org.apache.inlong.tubemq.manager.controller.topic.request.SetSubscribeReq;
import org.apache.inlong.tubemq.manager.service.TubeConst;
import org.apache.inlong.tubemq.manager.service.TubeMQErrorConst;
import org.apache.inlong.tubemq.manager.service.interfaces.MasterService;
import org.apache.inlong.tubemq.manager.service.interfaces.NodeService;
import org.apache.inlong.tubemq.manager.service.interfaces.TopicService;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(path = "/v1/topic")
@Slf4j
public class TopicWebController {

    private static final Logger LOGGER = LogManager.getLogger(TopicWebController.class);

    @Autowired
    private NodeService nodeService;

    private static final Gson gson = new Gson();

    @Autowired
    private MasterService masterService;

    @Autowired
    private TopicService topicService;

    /**
     * broker method proxy
     * divides the operation on broker to different method
     */
    @RequestMapping(value = "")
    public @ResponseBody TubeMQResult topicMethodProxy(@RequestParam String method, @RequestBody String req)
            throws Exception {
        // Log audit: Record the received method and req parameters
        LOGGER.info("Received method for topicMethodProxy: {}", method);
        LOGGER.info("Received req for topicMethodProxy: {}", req);

        // Validate the 'method' parameter to ensure it's one of the allowed methods
        if (!isValidMethod(method)) {
            // Log audit: Record the invalid 'method' value
            LOGGER.warn("Invalid method value received: {}", method);
            return TubeMQResult.errorResult("Invalid method value.");
        }

        // Validate the 'req' parameter to ensure it's a valid JSON format
        if (!isValidJson(req)) {
            // Log audit: Record the invalid JSON format
            LOGGER.warn("Invalid JSON format received: {}", req);
            return TubeMQResult.errorResult("Invalid JSON format.");
        }

        // Perform processing based on the 'method' parameter
        switch (method) {
            case TubeConst.ADD:
                return masterService.baseRequestMaster(gson.fromJson(req, BatchAddTopicReq.class));
            case TubeConst.CLONE:
                return nodeService.cloneTopicToBrokers(gson.fromJson(req, CloneTopicReq.class));
            case TubeConst.AUTH_CONTROL:
                return setAuthControl(gson.fromJson(req, SetAuthControlReq.class));
            case TubeConst.MODIFY:
                return masterService.baseRequestMaster(gson.fromJson(req, ModifyTopicReq.class));
            case TubeConst.DELETE:
            case TubeConst.REMOVE:
                return masterService.baseRequestMaster(gson.fromJson(req, DeleteTopicReq.class));
            case TubeConst.QUERY_CAN_WRITE:
                return queryCanWrite(gson.fromJson(req, QueryCanWriteReq.class));
            case TubeConst.PUBLISH:
                return masterService.baseRequestMaster(gson.fromJson(req, SetPublishReq.class));
            case TubeConst.SUBSCRIBE:
                return masterService.baseRequestMaster(gson.fromJson(req, SetSubscribeReq.class));
            default:
                return TubeMQResult.errorResult(TubeMQErrorConst.NO_SUCH_METHOD);
        }
    }

    private static boolean isValidMethod(String method) {
        // Define a list of allowed methods
        List<String> allowedMethods = Arrays.asList(
                TubeConst.ADD, TubeConst.CLONE, TubeConst.AUTH_CONTROL, TubeConst.MODIFY,
                TubeConst.DELETE, TubeConst.REMOVE, TubeConst.QUERY_CAN_WRITE, TubeConst.PUBLISH, TubeConst.SUBSCRIBE);
        return allowedMethods.contains(method);
    }

    private static boolean isValidJson(String json) {
        // Use a JSON library or parser to validate the JSON format
        try {
            gson.fromJson(json, Object.class);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private TubeMQResult setAuthControl(SetAuthControlReq req) {
        req.setMethod(TubeConst.SET_AUTH_CONTROL);
        req.setType(TubeConst.OP_MODIFY);
        req.setCreateUser(TubeConst.TUBEADMIN);
        return masterService.baseRequestMaster(req);
    }

    private TubeMQResult queryCanWrite(QueryCanWriteReq req) {
        if (!req.legal()) {
            return TubeMQResult.errorResult(TubeMQErrorConst.PARAM_ILLEGAL);
        }
        return topicService.queryCanWrite(req.getTopicName(), req.getClusterId());
    }

    /**
     * query consumer auth control, shows all consumer groups
     *
     * @param req
     * @return
     *
     * @throws Exception the exception
     */
    @GetMapping("/consumerAuth")
    public @ResponseBody String queryConsumerAuth(
            @RequestParam Map<String, String> req) throws Exception {
        String url = masterService.getQueryUrl(req);
        return masterService.queryMaster(url);
    }

    /**
     * query topic config info
     *
     * @param req
     * @return
     *
     * @throws Exception the exception
     */
    @GetMapping("/topicConfig")
    public @ResponseBody String queryTopicConfig(
            @RequestParam Map<String, String> req) throws Exception {
        String url = masterService.getQueryUrl(req);
        return masterService.queryMaster(url);
    }

}

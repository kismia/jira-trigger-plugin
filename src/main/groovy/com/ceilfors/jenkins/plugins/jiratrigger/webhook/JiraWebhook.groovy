package com.ceilfors.jenkins.plugins.jiratrigger.webhook
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Log
import hudson.Extension
import hudson.model.UnprotectedRootAction
import org.codehaus.jettison.json.JSONObject
import org.kohsuke.stapler.StaplerRequest
import org.kohsuke.stapler.interceptor.RequirePOST
import javax.inject.Inject
import java.util.logging.Level
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.PRIORITY_FIELD
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.RESOLUTION_FIELD
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.VOTES_FIELD
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.WATCHER_FIELD
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.TIMETRACKING_FIELD
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.ATTACHMENT_FIELD
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.ASSIGNEE_FIELD
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.REPORTER_FIELD
import static com.atlassian.jira.rest.client.api.domain.IssueFieldId.DESCRIPTION_FIELD
/**
 * The HTTP endpoint that receives JIRA Webhook.
 *
 * @author ceilfors
 */
@Log
@Extension
class JiraWebhook implements UnprotectedRootAction {
    public static final URL_NAME = 'jira-trigger-webhook-receiver'
    public static final ISSUE_UPDATED_WEBHOOK_EVENT = 'jira:issue_updated'
    public static final COMMENT_CREATED_WEBHOOK_EVENT = 'comment_created'
    public static final ISSUE_CREATOR_WEBHOOK_EVENT = 'creator'
    public static final USER_NAME_WEBHOOK_EVENT = 'name'
    public static final USER_KEY_WEBHOOK_EVENT = 'key'
    public static final USER_DISPLAY_NAME_WEBHOOK_EVENT = 'displayName'
    private JiraWebhookListener jiraWebhookListener
    private List<String> optionalNestedFields = [
            PRIORITY_FIELD.id,
            RESOLUTION_FIELD.id,
            VOTES_FIELD.id,
            WATCHER_FIELD.id,
            TIMETRACKING_FIELD.id,
            ATTACHMENT_FIELD.id,
            DESCRIPTION_FIELD.id,
    ]
    @Inject
    void setJiraWebhookListener(JiraWebhookListener jiraWebhookListener) {
        this.jiraWebhookListener = jiraWebhookListener
    }
    @Override
    String getIconFileName() {
        null
    }
    @Override
    String getDisplayName() {
        'JIRA Trigger'
    }
    @Override
    String getUrlName() {
        URL_NAME
    }
    @SuppressWarnings('GroovyUnusedDeclaration')
    @RequirePOST
    void doIndex(StaplerRequest request) {
        processEvent(request, getRequestBody(request))
    }
    void processEvent(StaplerRequest request, String webhookEvent) {
        logJson(webhookEvent)
        Map webhookEventMap = new JsonSlurper().parseText(webhookEvent) as Map
        RawWebhookEvent rawWebhookEvent = new RawWebhookEvent(request, webhookEventMap)
        JSONObject webhookJsonObject = new JSONObject(webhookEvent)
        JSONObject issue = webhookJsonObject.getJSONObject('issue')
        JSONObject issueFields = issue.getJSONObject('fields')
        optionalNestedFields.each {
            if (issueFields.has(it)) {
                issueFields.put(it, JSONObject.NULL)
            }
        }
        if (!issueFields.isNull(ISSUE_CREATOR_WEBHOOK_EVENT)) {
            JSONObject issueFieldsCreator = issueFields.getJSONObject(ISSUE_CREATOR_WEBHOOK_EVENT)
            if (!issueFieldsCreator.has(USER_NAME_WEBHOOK_EVENT)) {
                issueFieldsCreator.put(USER_NAME_WEBHOOK_EVENT, issueFieldsCreator.getString(USER_DISPLAY_NAME_WEBHOOK_EVENT))
                issueFieldsCreator.put(USER_KEY_WEBHOOK_EVENT, issueFieldsCreator.getString(USER_DISPLAY_NAME_WEBHOOK_EVENT))
            }
        }
        if (!issueFields.isNull(ASSIGNEE_FIELD.id)) {
            JSONObject issueFieldsAssignee = issueFields.getJSONObject(ASSIGNEE_FIELD.id)
            if (!issueFieldsAssignee.has(USER_NAME_WEBHOOK_EVENT)) {
                issueFieldsAssignee.put(USER_NAME_WEBHOOK_EVENT, issueFieldsAssignee.getString(USER_DISPLAY_NAME_WEBHOOK_EVENT))
                issueFieldsAssignee.put(USER_KEY_WEBHOOK_EVENT, issueFieldsAssignee.getString(USER_DISPLAY_NAME_WEBHOOK_EVENT))
            }
        }
        if (!issueFields.isNull(REPORTER_FIELD.id)) {
            JSONObject issueFieldsReporter = issueFields.getJSONObject(REPORTER_FIELD.id)
            if (!issueFieldsReporter.has(USER_NAME_WEBHOOK_EVENT)) {
                issueFieldsReporter.put(USER_NAME_WEBHOOK_EVENT, issueFieldsReporter.getString(USER_DISPLAY_NAME_WEBHOOK_EVENT))
                issueFieldsReporter.put(USER_KEY_WEBHOOK_EVENT, issueFieldsReporter.getString(USER_DISPLAY_NAME_WEBHOOK_EVENT))
            }
        }
        log.info(webhookJsonObject.toString())
        boolean validEvent = false
        if (rawWebhookEvent.isChangelogEvent()) {
            log.fine("Received Webhook callback from changelog. Event type: ${rawWebhookEvent.eventType}")
            WebhookChangelogEvent changelogEvent = new WebhookChangelogEventJsonParser().parse(webhookJsonObject)
            changelogEvent.userId = rawWebhookEvent.userId
            changelogEvent.userKey = rawWebhookEvent.userKey
            jiraWebhookListener.changelogCreated(changelogEvent)
            validEvent = true
        }
        if (rawWebhookEvent.isCommentEvent()) {
            log.fine("Received Webhook callback from comment. Event type: ${rawWebhookEvent.eventType}")
            WebhookCommentEvent commentEvent = new WebhookCommentEventJsonParser().parse(webhookJsonObject)
            commentEvent.userId = rawWebhookEvent.userId
            commentEvent.userKey = rawWebhookEvent.userKey
            jiraWebhookListener.commentCreated(commentEvent)
            validEvent = true
        }
        if (!validEvent) {
            log.warning('Received Webhook callback with an invalid event type or a body without comment/changelog. ' +
                    "Event type: ${rawWebhookEvent.eventType}. Event body contains: ${webhookEventMap.keySet()}.")
        }
    }
    private void logJson(String webhookEvent) {
        if (log.isLoggable(Level.FINEST)) {
            log.finest('Webhook event body:')
            log.finest(JsonOutput.prettyPrint(webhookEvent))
        }
    }
    private String getRequestBody(StaplerRequest req) {
        req.inputStream.text
    }
    private static class RawWebhookEvent {
        final StaplerRequest request
        final Map webhookEventMap
        RawWebhookEvent(StaplerRequest request, Map webhookEventMap) {
            this.request = request
            this.webhookEventMap = webhookEventMap
        }
        boolean isChangelogEvent() {
            eventType == ISSUE_UPDATED_WEBHOOK_EVENT && webhookEventMap['changelog']
        }
        boolean isCommentEvent() {
            (eventType == ISSUE_UPDATED_WEBHOOK_EVENT
                    || eventType == COMMENT_CREATED_WEBHOOK_EVENT) && webhookEventMap['comment']
        }
        String getUserId() {
            request.getParameter('user_id')
        }
        String getUserKey() {
            request.getParameter('user_key')
        }
        String getEventType() {
            webhookEventMap['webhookEvent']
        }
    }
}
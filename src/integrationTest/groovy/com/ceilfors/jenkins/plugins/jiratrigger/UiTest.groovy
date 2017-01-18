package com.ceilfors.jenkins.plugins.jiratrigger

import com.ceilfors.jenkins.plugins.jiratrigger.changelog.CustomFieldChangelogMatcher
import com.ceilfors.jenkins.plugins.jiratrigger.changelog.JiraFieldChangelogMatcher
import com.ceilfors.jenkins.plugins.jiratrigger.parameter.IssueAttributePathParameterMapping
import com.ceilfors.jenkins.plugins.jiratrigger.ui.JiraChangelogTriggerConfigurer
import com.ceilfors.jenkins.plugins.jiratrigger.ui.JiraCommentTriggerConfigurer
import com.ceilfors.jenkins.plugins.jiratrigger.ui.JiraTriggerConfigurer
import com.ceilfors.jenkins.plugins.jiratrigger.ui.JiraTriggerGlobalConfigurationPage
import hudson.model.FreeStyleProject
import jenkins.model.GlobalConfiguration
import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

import static org.hamcrest.Matchers.hasItem
import static org.hamcrest.Matchers.instanceOf
import static org.junit.Assert.assertThat
/**
 * @author ceilfors
 */
class UiTest extends Specification {

    @Rule
    JenkinsRule jenkins = new JenkinsRule()

    JiraTriggerConfigurer createUiConfigurer(Class triggerType) {
        if (triggerType == JiraCommentTrigger) {
            new JiraCommentTriggerConfigurer(jenkins, 'job')
        } else if (triggerType == JiraChangelogTrigger) {
            new JiraChangelogTriggerConfigurer(jenkins, 'job')
        } else {
            throw new UnsupportedOperationException("Trigger $triggerType is unsupported")
        }
    }

    def 'Sets Global configuration'() {
        given:
        def configPage = new JiraTriggerGlobalConfigurationPage(jenkins.createWebClient().goTo('configure'))

        when:
        configPage.setRootUrl('test root')
        configPage.setCredentials('test user', 'test password')
        configPage.setJiraCommentReply(true)
        configPage.save()

        then:
        def globalConfig = GlobalConfiguration.all().get(JiraTriggerGlobalConfiguration)
        globalConfig.jiraCommentReply
        globalConfig.jiraRootUrl == 'test root'
        globalConfig.jiraUsername == 'test user'
        globalConfig.jiraPassword.plainText == 'test password'
    }

    def 'Sets JQL filter'() {
        given:
        def jqlFilter = 'non default jql filter'
        FreeStyleProject project = jenkins.createFreeStyleProject('job')
        JiraTriggerConfigurer configurer = createUiConfigurer(triggerType, jenkins, 'job')

        when:
        configurer.activate()

        then:
        assertThat(project.triggers.values(), hasItem(instanceOf(triggerType)))

        when:
        configurer.setJqlFilter(jqlFilter)
        def trigger = project.getTrigger(triggerType)

        then:
        trigger.jqlFilter == jqlFilter

        where:
        triggerType << [JiraCommentTrigger, JiraChangelogTrigger]
    }

    def 'Adds parameter mappings'() {
        given:
        FreeStyleProject project = jenkins.createFreeStyleProject('job')
        JiraTriggerConfigurer configurer = createUiConfigurer(triggerType, jenkins, 'job')

        when:
        configurer.activate()
        configurer.addParameterMapping('parameter1', 'path1')
        configurer.addParameterMapping('parameter2', 'path2')
        def trigger = project.getTrigger(triggerType)

        then:
        trigger.parameterMappings.size() == 2
        trigger.parameterMappings[0] == new IssueAttributePathParameterMapping('parameter1', 'path1')
        trigger.parameterMappings[1] == new IssueAttributePathParameterMapping('parameter2', 'path2')

        where:
        triggerType << [JiraCommentTrigger, JiraChangelogTrigger]
    }

    def 'Sets comment pattern'() {
        given:
        def commentPattern = 'non default comment pattern'
        FreeStyleProject project = jenkins.createFreeStyleProject('job')
        def configurer = new JiraCommentTriggerConfigurer(jenkins, 'job')

        when:
        configurer.activate()
        configurer.setCommentPattern(commentPattern)
        def trigger = project.getTrigger(JiraCommentTrigger)

        then:
        trigger.commentPattern == commentPattern
    }

    def 'Adds field matchers'() {
        given:
        FreeStyleProject project = jenkins.createFreeStyleProject('job')
        def configurer = new JiraChangelogTriggerConfigurer(jenkins, 'job')

        when:
        configurer.activate()
        configurer.addCustomFieldChangelogMatcher('Custom Field 1', 'old 1', 'new 1')
        configurer.addJiraFieldChangelogMatcher('Jira Field 1', 'old 2', 'new 2')
        configurer.addCustomFieldChangelogMatcher('Custom Field 2', 'old 3', 'new 3')
        configurer.addJiraFieldChangelogMatcher('Jira Field 2', 'old 4', 'new 4')
        def matchers = project.getTrigger(JiraChangelogTrigger).changelogMatchers

        then:
        matchers.size() == 4
        matchers[0] == new CustomFieldChangelogMatcher('Custom Field 1', 'new 1', 'old 1', true, true)
        matchers[1] == new JiraFieldChangelogMatcher('Jira Field 1', 'new 2', 'old 2', true, true)
        matchers[2] == new CustomFieldChangelogMatcher('Custom Field 2', 'new 3', 'old 3', true, true)
        matchers[3] == new JiraFieldChangelogMatcher('Jira Field 2', 'new 4', 'old 4', true, true)
    }
}

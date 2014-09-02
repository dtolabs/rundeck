<g:set var="rkey" value="${g.rkey()}" />
<g:render template="runningExecutions" model="[executions:nowrunning,jobs:jobs,nowrunning:true,idprefix:rkey,emptyText:'']"/>
    <g:if test="${total && max && total.toInteger() > max.toInteger()}">
        <span class="info note">Showing <g:enc>${nowrunning.size()}of ${total}</g:enc></span>
    </g:if>
<g:if test="${total>max}">
<span class="paginate"><g:paginate action="nowrunning" total="${total}"  max="${max}"/></span>
</g:if>
<g:render template="/common/boxinfo" model="${[name:'nowrunning',model:[title:'Now Running',total:total,lastExecId:lastExecId]]}"/>

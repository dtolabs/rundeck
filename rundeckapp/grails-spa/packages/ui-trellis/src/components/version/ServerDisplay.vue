<template>
    <span :title="title">
        <span :style="{color: `rgba(${color}, 219, 183)`}">
            <i class="glyphicon" :class="[`glyphicon-${glyphicon}`]" />
            {{uuidShort}}
        </span>
        <span style="margin-left: 0.5em">{{name}}</span>
    </span>
</template>

<script lang="ts">
import Vue, {PropType} from 'vue'

import {ServerInfo} from '../../stores/System'
import { RundeckVersion } from '../../utilities/RundeckVersion'

export default Vue.extend({
    props: {
        glyphicon: String,
        uuid: String,
        name: String
    },
    computed: {
        color(): number {
            const ver = new RundeckVersion({})
            return ver.splitUUID(this.uuid)['uuid0']
        },
        uuidShort(): string {
            return this.uuid.substr(0, 2)
        },
        title(): string {
            return `${this.glyphicon}-${this.uuidShort} / ${this.uuid}`
        }
    }
})
</script>
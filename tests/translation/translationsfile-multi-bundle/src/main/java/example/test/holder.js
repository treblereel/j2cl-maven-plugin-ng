goog.module('holder');

const jre = goog.require('jre');

/** @define {string} */
const value = goog.define('holder.value', 'unknown');

jre.addSystemPropertyFromGoogDefine('holder.value', value);

exports = {
    value,
};

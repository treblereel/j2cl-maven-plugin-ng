goog.module('holder');

const jre = goog.require('jre');

/** @define {string} */
const value = goog.define('holder.value', 'unknown');

/** @define {string} */
const aptValue = goog.define('holder.aptValue', 'unknown');

jre.addSystemPropertyFromGoogDefine('holder.value', value);
jre.addSystemPropertyFromGoogDefine('holder.aptValue', aptValue);

exports = {
    value,
    aptValue,
};

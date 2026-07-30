/** @desc A greeting message displayed on the page */
var MSG_HELLO_WORLD = goog.getMsg("Hello, World!");

/** @desc Button label prompting the user to click */
var MSG_CLICK_ME = goog.getMsg("Click me!");

/** @desc Personalized greeting with a name placeholder */
var MSG_GREETING = goog.getMsg("Welcome, {$name}!", {"name": "\x01"});

Messages.helloWorld = function () {
    return MSG_HELLO_WORLD;
}

Messages.clickMe = function () {
    return MSG_CLICK_ME;
}

Messages.greeting = function (/** string */ name) {
    return MSG_GREETING.replace("\x01", name);
}

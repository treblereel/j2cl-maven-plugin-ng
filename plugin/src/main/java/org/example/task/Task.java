package org.example.task;

import java.io.File;
import java.util.function.Supplier;

public interface Task extends Supplier<File> {
}
